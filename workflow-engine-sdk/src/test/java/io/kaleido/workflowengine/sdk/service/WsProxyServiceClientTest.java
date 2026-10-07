// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.sdk.core.http.ServiceClient;
import io.kaleido.sdk.core.http.ServiceClientException;
import io.kaleido.sdk.core.http.ServiceClientOptions;
import io.kaleido.workflowengine.sdk.protocol.WSMessageType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** A {@link ServiceClient} for a hosted binding, through the real {@link WSProxyAdapter}. */
class WsProxyServiceClientTest {

    record Count(int count) {
    }

    /** Answers each service proxy request as the provider-proxy would, recording it. */
    private static final class FakeProxy implements ProxyAdapterRuntime {
        final WSProxyAdapter adapter = new WSProxyAdapter(2000);
        final List<ServiceProxyRequest> sent = new CopyOnWriteArrayList<>();
        volatile int status = 200;
        volatile String error;

        FakeProxy() {
            adapter.setRuntime(this);
        }

        @Override
        public void sendMessage(Object message) {
            var request = (ServiceProxyRequest) message;
            sent.add(request);
            var body = Base64.getEncoder().encodeToString("{\"count\":7}".getBytes(StandardCharsets.UTF_8));
            Thread.ofVirtual().start(() -> adapter.handleResponse(new ServiceProxyResponse(
                    WSMessageType.SERVICE_PROXY_RESPONSE, request.requestId(), status, Map.of(), body, error)));
        }

        @Override
        public boolean isWebSocketConnected() {
            return true;
        }
    }

    @Test
    void aHostedBindingGoesThroughTheProviderProxyWithTheAuthRef() {
        var proxy = new FakeProxy();
        var client = new ServiceClient(new ServiceClientOptions.WsProxy(proxy.adapter, "AssetManagerService",
                "s:am1", "ar-123"));

        var count = client.post("/api/v1/assets", Map.of("name", "x"), Count.class);

        assertEquals(7, count.count());
        var sent = proxy.sent.getFirst();
        assertEquals("s:am1", sent.id());
        assertEquals("ar-123", sent.authRef());
        assertEquals("POST", sent.request().method());
        assertEquals("/api/v1/assets", sent.request().path());
        assertEquals("{\"name\":\"x\"}",
                new String(Base64.getDecoder().decode(sent.request().bodyBase64()), StandardCharsets.UTF_8));
    }

    @Test
    void aHostedBindingKeepsTheStatusOfAProxyError() {
        var proxy = new FakeProxy();
        proxy.status = 404;
        proxy.error = "unknown service instance id \"s:am1\"";
        var client = new ServiceClient(new ServiceClientOptions.WsProxy(proxy.adapter, "AssetManagerService",
                "s:am1", null));

        var error = assertThrows(ServiceClientException.class, () -> client.get("/api/v1/assets", JsonNode.class));
        assertEquals(404, error.status());
        assertTrue(error.getMessage().contains("unknown service instance id"), error.getMessage());
    }

    /** A body Jackson cannot write. */
    public static final class Unwritable {
        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void aHostedBodyThatCannotBeWrittenIsTheCallersError() {
        var client = new ServiceClient(new ServiceClientOptions.WsProxy(new FakeProxy().adapter,
                "AssetManagerService", "s:am1", null));

        assertThrows(IllegalArgumentException.class, () -> client.post("/things", new Unwritable(), JsonNode.class));
    }

    @Test
    void aHostedCallKeepsTheThreadInterrupted() {
        var client = new ServiceClient(new ServiceClientOptions.WsProxy(new FakeProxy().adapter,
                "AssetManagerService", "s:am1", null));

        Thread.currentThread().interrupt();
        try {
            var error = assertThrows(ServiceClientException.class, () -> client.get("/things", JsonNode.class));
            assertTrue(error.getMessage().contains("interrupted"), error.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
