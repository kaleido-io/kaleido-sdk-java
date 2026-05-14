// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.server;

import io.kaleido.wfe.sdk.config.RuntimeConfig;
import io.kaleido.wfe.sdk.config.ServerConfig;
import io.kaleido.wfe.sdk.handlers.HandlerSetFor;
import io.kaleido.wfe.sdk.handlers.TransactionHandler;
import io.kaleido.wfe.sdk.protocol.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors {@code outbound_conn_test.go} in the Go engine: stand a SDK server up,
 * connect with a stock {@link java.net.http.WebSocket} acting as the engine,
 * verify the SDK sends {@link WSRegisterProvider} then {@link WSRegisterHandler},
 * then exercise a round-trip {@link WSHandleTransactions} / result.
 */
class WFEWebSocketServerTest {

    private WFEWebSocketServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void registersAndRoundTripsTransactionBatch() throws Exception {
        var handler = new TestTransactionHandler();
        var runtimeConfig = RuntimeConfig.builder().providerName("test-provider").build();
        var serverConfig = new ServerConfig("localhost", 0);

        server = new WFEWebSocketServer(runtimeConfig, serverConfig, HandlerSetFor.of(handler));
        server.start();
        assertTrue(server.isRunning());
        int port = server.getPort();
        assertTrue(port > 0);

        var messages = new LinkedBlockingQueue<String>();
        var listener = new CollectingListener(messages);
        var wsClient = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), listener)
                .get(5, TimeUnit.SECONDS);

        var first = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(first, "expected RegisterProvider message");
        var registerProvider = JSON.MAPPER.readValue(first, WSRegisterProvider.class);
        assertEquals(WSMessageType.REGISTER_PROVIDER, registerProvider.messageType());
        assertEquals("test-provider", registerProvider.providerName());

        var second = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(second, "expected RegisterHandler message");
        var registerHandler = JSON.MAPPER.readValue(second, WSRegisterHandler.class);
        assertEquals(WSMessageType.REGISTER_HANDLER, registerHandler.messageType());
        assertEquals(WSHandlerType.TRANSACTION_HANDLER, registerHandler.handlerType());
        assertEquals("test-handler", registerHandler.handler());

        var request = new WSHandleTransactions(
                WSMessageType.HANDLE_TRANSACTIONS, "req-1",
                WSHandlerType.TRANSACTION_HANDLER, "test-handler",
                null, null,
                List.of(new WSHandleTransaction(
                        "txn-1", "wf-1", "op-1", 0L, "idem-1", 0,
                        null, null, "init", null, null,
                        null, null,
                        JSON.MAPPER.createObjectNode().put("hello", "world"),
                        null, null, null)));

        wsClient.sendText(JSON.MAPPER.writeValueAsString(request), true).get(5, TimeUnit.SECONDS);

        var responseJson = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(responseJson, "expected handle_transactions_result");
        var result = JSON.MAPPER.readValue(responseJson, WSHandleTransactionsResult.class);
        assertEquals(WSMessageType.HANDLE_TRANSACTIONS_RESULT, result.messageType());
        assertEquals("req-1", result.id());
        assertEquals(1, result.results().size());
        assertEquals("done", result.results().get(0).stage());

        wsClient.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }

    @Test
    void serverAcceptsConnectionWithoutAuth() throws Exception {
        var runtimeConfig = RuntimeConfig.builder().providerName("test-provider").build();
        var serverConfig = new ServerConfig("localhost", 0);

        server = new WFEWebSocketServer(runtimeConfig, serverConfig, HandlerSetFor.of());
        server.start();
        int port = server.getPort();

        var messages = new LinkedBlockingQueue<String>();
        var listener = new CollectingListener(messages);
        var wsClient = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws"), listener)
                .get(5, TimeUnit.SECONDS);

        var first = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(first, "RegisterProvider should arrive even with zero handlers");
        var registerProvider = JSON.MAPPER.readValue(first, WSRegisterProvider.class);
        assertEquals("test-provider", registerProvider.providerName());

        wsClient.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }

    private static class CollectingListener implements WebSocket.Listener {
        private final BlockingQueue<String> messages;
        private final StringBuilder buf = new StringBuilder();

        CollectingListener(BlockingQueue<String> messages) {
            this.messages = messages;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                messages.add(buf.toString());
                buf.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
    }

    private static class TestTransactionHandler implements TransactionHandler {
        @Override
        public String name() { return "test-handler"; }

        @Override
        public WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) {
            var results = new ArrayList<WSHandleTransactionResult>();
            request.transactions().forEach(txn -> results.add(WSHandleTransactionResult.stage("done")));
            return WSHandleTransactionsResult.forRequest(request, results);
        }
    }
}
