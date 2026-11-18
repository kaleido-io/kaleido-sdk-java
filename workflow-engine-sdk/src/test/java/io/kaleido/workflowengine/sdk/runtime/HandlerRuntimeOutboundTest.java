// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import io.kaleido.workflowengine.sdk.config.ClientConfig;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-socket tests of outbound mode over plain ws. */
class HandlerRuntimeOutboundTest {

    /** A plain WebSocket server standing in for the engine, recording every text message. */
    private static class EngineStub implements AutoCloseable {
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private final CountDownLatch started = new CountDownLatch(1);
        private final WebSocketServer server;

        EngineStub() throws Exception {
            server = new WebSocketServer(new InetSocketAddress("127.0.0.1", 0)) {
                @Override
                public void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake) {
                }

                @Override
                public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {
                }

                @Override
                public void onMessage(org.java_websocket.WebSocket conn, String message) {
                    received.add(message);
                }

                @Override
                public void onError(org.java_websocket.WebSocket conn, Exception ex) {
                }

                @Override
                public void onStart() {
                    started.countDown();
                }
            };
            server.start();
            assertTrue(started.await(10, TimeUnit.SECONDS), "test server did not start");
        }

        int port() {
            return server.getPort();
        }

        @Override
        public void close() throws Exception {
            server.stop();
        }
    }

    /**
     * Handlers reply from their own virtual threads, so sends race. The JDK
     * WebSocket allows one text send in flight and throws "Send pending" for
     * the rest. Check that's handled properly, so all concurrent send arrive.
     */
    @Test
    void concurrentSendsAreAllDelivered() throws Exception {
        try (var engine = new EngineStub()) {
            var runtime = new HandlerRuntime(ClientConfig.builder()
                    .providerName("outbound-test")
                    .wsUrl(URI.create("ws://127.0.0.1:" + engine.port() + "/ws"))
                    .maxAttempts(1)
                    .build());
            runtime.start();
            try {
                assertNotNull(engine.received.poll(10, TimeUnit.SECONDS), "expected register_provider");

                // Large payloads keep each send in flight long enough to overlap.
                var padding = "x".repeat(256 * 1024);
                int count = 50;
                var ready = new CountDownLatch(1);
                try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (int i = 0; i < count; i++) {
                        var id = "msg-" + i;
                        pool.submit(() -> {
                            ready.await();
                            runtime.sendMessage(Map.of("id", id, "padding", padding));
                            return null;
                        });
                    }
                    ready.countDown();
                }

                var ids = new HashSet<String>();
                for (int i = 0; i < count; i++) {
                    var msg = engine.received.poll(10, TimeUnit.SECONDS);
                    assertNotNull(msg, "only received " + ids.size() + " of " + count + " messages");
                    ids.add(JSON.MAPPER.readTree(msg).path("id").asText());
                }
                assertEquals(count, ids.size());
            } finally {
                runtime.stop();
            }
        }
    }
}
