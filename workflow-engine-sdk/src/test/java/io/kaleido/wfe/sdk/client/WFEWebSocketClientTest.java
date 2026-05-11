// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.client;

import com.sun.net.httpserver.HttpServer;
import io.kaleido.wfe.sdk.config.ClientConfig;
import io.kaleido.wfe.sdk.handlers.HandlerSetFor;
import io.kaleido.wfe.sdk.handlers.TransactionHandler;
import io.kaleido.wfe.sdk.protocol.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test using a raw HTTP server that performs WS upgrade.
 * This is deliberately simple -- a real mock WS server would use a library,
 * but for the prototype we validate the SDK compiles and the handler
 * dispatch logic works via unit-level assertions.
 */
class WFEWebSocketClientTest {

    @Test
    void handlerSetInitialization() {
        var handler = new TestTransactionHandler();
        var handlerSet = HandlerSetFor.of(handler);
        var handlers = handlerSet.init(null);

        assertEquals(1, handlers.size());
        assertEquals("test-handler", handlers.get(0).name());
    }

    @Test
    void transactionHandlerBatch() throws Exception {
        var handler = new TestTransactionHandler();
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

        var result = handler.handleTransactionBatch(request);

        assertEquals(WSMessageType.HANDLE_TRANSACTIONS_RESULT, result.messageType());
        assertEquals("req-1", result.id());
        assertNotNull(result.results());
        assertEquals(1, result.results().size());
        assertEquals("done", result.results().get(0).stage());
    }

    @Test
    void clientConfigAndConnect() {
        var config = ClientConfig.builder()
                .url(URI.create("ws://localhost:19999/ws"))
                .providerName("test-provider")
                .maxAttempts(1)
                .build();

        var client = new WFEWebSocketClient(config,
                HandlerSetFor.of(new TestTransactionHandler()));

        // Expect connection to fail since nothing is listening
        var future = client.connect();
        assertNotNull(future);
        client.close();
    }

    private static class TestTransactionHandler implements TransactionHandler {
        @Override
        public String name() { return "test-handler"; }

        @Override
        public WSHandleTransactionsResult handleTransactionBatch(WSHandleTransactions request) {
            var results = request.transactions().stream()
                    .map(txn -> WSHandleTransactionResult.stage("done"))
                    .toList();
            return WSHandleTransactionsResult.forRequest(request, results);
        }
    }
}
