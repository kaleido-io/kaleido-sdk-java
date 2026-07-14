// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

import io.kaleido.workflowengine.sdk.protocol.WSMessageType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class WSProxyAdapterTest {

    private static class StubRuntime implements ProxyAdapterRuntime {
        final List<Object> sent = new CopyOnWriteArrayList<>();
        volatile boolean connected = true;

        @Override
        public void sendMessage(Object message) {
            sent.add(message);
        }

        @Override
        public boolean isWebSocketConnected() {
            return connected;
        }
    }

    @Test
    void correlatesRequestAndResponse() throws Exception {
        var runtime = new StubRuntime();
        var adapter = new WSProxyAdapter();
        adapter.setRuntime(runtime);

        var pending = CompletableFuture.supplyAsync(() -> {
            try {
                return adapter.request("asset-manager", "GET", "svc-1", null, null, "/api/v1/assets", "auth-1");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for the request to be sent, then reply with its requestId
        ServiceProxyRequest request = null;
        for (var i = 0; i < 50 && request == null; i++) {
            Thread.sleep(20);
            request = runtime.sent.isEmpty() ? null : (ServiceProxyRequest) runtime.sent.get(0);
        }
        assertNotNull(request);
        assertEquals(WSMessageType.SERVICE_PROXY_REQUEST, request.messageType());
        assertEquals("asset-manager", request.serviceType());
        assertEquals("svc-1", request.id());
        assertEquals("auth-1", request.authRef());
        assertEquals("GET", request.request().method());
        assertEquals("/api/v1/assets", request.request().path());

        adapter.handleResponse(new ServiceProxyResponse(
                WSMessageType.SERVICE_PROXY_RESPONSE, request.requestId(), 200, null, null, null));

        var response = pending.get();
        assertEquals(200, response.status());
    }

    @Test
    void errorResponseRejects() throws Exception {
        var runtime = new StubRuntime();
        var adapter = new WSProxyAdapter();
        adapter.setRuntime(runtime);

        var pending = CompletableFuture.supplyAsync(() -> {
            try {
                return adapter.request("key-manager", "POST", "svc-2", null, null, null, null);
            } catch (Exception e) {
                throw new CompletionWrapper(e);
            }
        });

        ServiceProxyRequest request = null;
        for (var i = 0; i < 50 && request == null; i++) {
            Thread.sleep(20);
            request = runtime.sent.isEmpty() ? null : (ServiceProxyRequest) runtime.sent.get(0);
        }
        assertNotNull(request);

        adapter.handleResponse(new ServiceProxyResponse(
                WSMessageType.SERVICE_PROXY_RESPONSE, request.requestId(), 500, null, null, "upstream failed"));

        var thrown = assertThrows(Exception.class, pending::get);
        assertTrue(thrown.getCause() instanceof CompletionWrapper);
        assertTrue(thrown.getCause().getCause().getMessage().contains("upstream failed"));
    }

    @Test
    void errorWithSuccessStatusResolves() throws Exception {
        var runtime = new StubRuntime();
        var adapter = new WSProxyAdapter();
        adapter.setRuntime(runtime);

        var pending = CompletableFuture.supplyAsync(() -> {
            try {
                return adapter.request("apigw", "GET", "svc-3", null, null, null, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        ServiceProxyRequest request = null;
        for (var i = 0; i < 50 && request == null; i++) {
            Thread.sleep(20);
            request = runtime.sent.isEmpty() ? null : (ServiceProxyRequest) runtime.sent.get(0);
        }
        assertNotNull(request);

        // A 2xx status with a (stale) error string still resolves
        adapter.handleResponse(new ServiceProxyResponse(
                WSMessageType.SERVICE_PROXY_RESPONSE, request.requestId(), 204, null, null, "ignored"));
        assertEquals(204, pending.get().status());
    }

    @Test
    void requestTimesOut() {
        var runtime = new StubRuntime();
        var adapter = new WSProxyAdapter(300);
        adapter.setRuntime(runtime);

        var thrown = assertThrows(TimeoutException.class, () ->
                adapter.request("asset-manager", "GET", "svc-1", null, null, null, null));
        assertTrue(thrown.getMessage().contains("timed out"));
    }

    @Test
    void cancelAllRejectsInflight() throws Exception {
        var runtime = new StubRuntime();
        var adapter = new WSProxyAdapter();
        adapter.setRuntime(runtime);

        var pending = CompletableFuture.supplyAsync(() -> {
            try {
                return adapter.request("asset-manager", "GET", "svc-1", null, null, null, null);
            } catch (Exception e) {
                throw new CompletionWrapper(e);
            }
        });

        for (var i = 0; i < 50 && runtime.sent.isEmpty(); i++) {
            Thread.sleep(20);
        }
        assertFalse(runtime.sent.isEmpty());

        adapter.cancelAll();
        var thrown = assertThrows(Exception.class, pending::get);
        assertTrue(thrown.getCause().getCause().getMessage().contains("connection closed"));
    }

    @Test
    void failsWhenNeverConnected() {
        var runtime = new StubRuntime();
        runtime.connected = false;
        var adapter = new WSProxyAdapter(300);
        adapter.setRuntime(runtime);

        assertThrows(IllegalStateException.class, () ->
                adapter.request("asset-manager", "GET", "svc-1", null, null, null, null));
    }

    private static class CompletionWrapper extends RuntimeException {
        CompletionWrapper(Throwable cause) {
            super(cause);
        }
    }
}
