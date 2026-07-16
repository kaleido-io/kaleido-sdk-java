// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import io.kaleido.workflowengine.sdk.config.ClientConfig;
import io.kaleido.workflowengine.sdk.config.ServerConfig;
import io.kaleido.workflowengine.sdk.handlers.RequestContext;
import io.kaleido.workflowengine.sdk.handlers.TransactionHandler;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.WSEvaluateReplyResult;
import io.kaleido.workflowengine.sdk.protocol.WSHandleTransactionsResult;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Real-socket test of inbound mode: the runtime binds a WebSocket server and
 * a plain client (standing in for the engine/provider-proxy) dials in,
 * exactly the direction this platform's hosted-provider topology requires.
 */
class HandlerRuntimeInboundTest {

    private static final int TEST_PORT = 26551;

    /** Minimal client-side text-frame collector, standing in for the engine. */
    private static class LineCollector implements WebSocket.Listener {
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                received.add(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
    }

    @Test
    void engineDialsInAndExchangesMessages() throws Exception {
        var runtime = new HandlerRuntime(ClientConfig.builder()
                .providerName("inbound-test")
                .server(new ServerConfig("127.0.0.1", TEST_PORT, null))
                .build());

        var received = new LinkedBlockingQueue<WSHandleTransactionsResult>();
        runtime.registerTransactionHandler("echo", new TransactionHandler() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public void transactionHandlerBatch(RequestContext reqContext,
                                                 WSHandleTransactionsResult result,
                                                 io.kaleido.workflowengine.sdk.protocol.WSHandleTransactions batch) {
                for (var txn : batch.transactions()) {
                    result.getResults().add(WSEvaluateReplyResult.stage("done-" + txn.transactionId()));
                }
                received.add(result);
            }
        });

        runtime.start();
        try {
            var listener = new LineCollector();
            var client = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create("ws://127.0.0.1:" + TEST_PORT), listener)
                    .get(5, TimeUnit.SECONDS);

            // On connect, the server-side runtime sends register_provider first.
            var registerMsg = listener.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(registerMsg, "expected register_provider on connect");
            var registerNode = JSON.MAPPER.readTree(registerMsg);
            assertEquals("register_provider", registerNode.path("messageType").asText());
            assertEquals("inbound-test", registerNode.path("providerName").asText());

            // Then register_handler for the one registered transaction handler.
            var registerHandlerMsg = listener.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(registerHandlerMsg, "expected register_handler on connect");

            client.sendText("""
                    {"messageType":"handle_transactions","id":"batch-1","handler":"echo",
                     "transactions":[
                       {"transactionId":"txn-1","workflowId":"wf-1","stage":"init","input":{}}
                     ]}
                    """, true).get(5, TimeUnit.SECONDS);

            var result = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(result, "handler was not invoked over the inbound connection");
            assertEquals("done-txn-1", result.getResults().get(0).getStage());

            var resultMsg = listener.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(resultMsg, "expected a handle_transactions result sent back to the client");
            assertNull(JSON.MAPPER.readTree(resultMsg).get("error"));
        } finally {
            runtime.stop();
        }
    }
}
