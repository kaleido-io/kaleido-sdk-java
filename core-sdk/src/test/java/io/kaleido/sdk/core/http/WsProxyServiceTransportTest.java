// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WsProxyServiceTransportTest {

    record Count(int count) {
    }

    private static ServiceClient client(ServiceProxy proxy) {
        return new ServiceClient(new ServiceClientOptions.WsProxy(proxy, "AssetManagerService", "s:am1", "ar-123"));
    }

    private static ServiceProxy failing(Exception e) {
        return (serviceType, id, authRef, method, path, headers, body) -> {
            throw e;
        };
    }

    @Test
    void aRequestCarriesTheBindingAndAuthRefToTheProxy() {
        var sent = new ArrayList<String>();
        ServiceProxy proxy = (serviceType, id, authRef, method, path, headers, body) -> {
            sent.add(serviceType + " " + id + " " + authRef + " " + method + " " + path + " " + headers + " " + body);
            return new ServiceResponse(200, Map.of("X-Trace-Id", "t1"), "{\"count\":7}".getBytes(StandardCharsets.UTF_8));
        };

        var response = client(proxy).request("POST", "/api/v1/assets", Map.of("name", "x"));

        assertEquals(7, response.json(Count.class).count());
        assertEquals("t1", response.headers().get("x-trace-id"));
        assertEquals(List.of("AssetManagerService s:am1 ar-123 POST /api/v1/assets "
                + "{Content-Type=application/json} {name=x}"), sent);
    }

    @Test
    void aRequestWithoutABodySendsNoContentType() {
        var sent = new ArrayList<Map<String, String>>();
        ServiceProxy proxy = (serviceType, id, authRef, method, path, headers, body) -> {
            sent.add(headers);
            return new ServiceResponse(204, null, null);
        };

        client(proxy).delete("/things/1");

        assertEquals(List.of(Map.of()), sent);
    }

    @Test
    void anErrorStatusFromTheServiceThrowsLikeHttp() {
        ServiceProxy proxy = (serviceType, id, authRef, method, path, headers, body) ->
                new ServiceResponse(409, null, "conflict".getBytes(StandardCharsets.UTF_8));

        var error = assertThrows(ServiceClientException.class, () -> client(proxy).get("/things", JsonNode.class));
        assertEquals(409, error.status());
        assertEquals("conflict", error.body());
    }

    @Test
    void aProxyErrorKeepsItsStatus() {
        var error = assertThrows(ServiceClientException.class,
                () -> client(failing(new ServiceProxyException(404, "unknown service instance id")))
                        .get("/things", JsonNode.class));

        assertEquals(404, error.status());
        assertFalse(error.retryable());
        assertTrue(error.getMessage().contains("unknown service instance id"), error.getMessage());
        assertInstanceOf(ServiceProxyException.class, error.getCause());
    }

    @Test
    void aProxyThatCannotBeReachedIsRetryable() {
        var error = assertThrows(ServiceClientException.class,
                () -> client(failing(new IllegalStateException("not connected"))).get("/things", JsonNode.class));

        assertEquals(0, error.status());
        assertTrue(error.retryable());
        assertTrue(error.getMessage().contains("via the provider-proxy failed: not connected"), error.getMessage());
    }

    @Test
    void aBodyTheProxyCannotWriteIsTheCallersError() {
        var proxy = failing(new JsonProcessingException("cannot write") {
        });

        assertThrows(IllegalArgumentException.class, () -> client(proxy).post("/things", Map.of(), JsonNode.class));
    }

    @Test
    void anInterruptedCallKeepsTheThreadInterrupted() {
        try {
            var error = assertThrows(ServiceClientException.class,
                    () -> client(failing(new InterruptedException())).get("/things", JsonNode.class));

            assertTrue(error.getMessage().contains("interrupted"), error.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
