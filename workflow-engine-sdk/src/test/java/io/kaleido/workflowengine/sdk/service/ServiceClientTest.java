// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.kaleido.workflowengine.sdk.protocol.WSMessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ServiceClientTest {

    record Count(int count) {
    }

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private volatile int status = 200;
    private volatile String errorBody = "nope";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + exchange.getRequestHeaders().getFirst("X-Api-Key") + " " + body);
            var code = failuresRemaining.getAndDecrement() > 0 ? 503 : status;
            var reply = (code == 200 ? "{\"count\":2}" : errorBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Trace-Id", "t1");
            exchange.sendResponseHeaders(code, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ServiceClient http(ServiceBindingAuth auth, Integer maxRetries) {
        return new ServiceClient(new ServiceClientOptions.Http(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/", auth, maxRetries, 5000));
    }

    @Test
    void aNonHostedBindingCallsItsUrlWithBasicAuthAndJson() {
        var count = http(ServiceBindingAuth.basic("user", "pass"), null).post("/things", Map.of("a", 1), Count.class);

        assertEquals(2, count.count());
        var basic = Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("POST /api/v1/things Basic " + basic + " null {\"a\":1}"), requests);
    }

    @Test
    void tokenAuthUsesItsHeaderAndScheme() {
        http(ServiceBindingAuth.token("t0k", "X-Api-Key", null), null).get("things", JsonNode.class);
        http(ServiceBindingAuth.token("t0k", null, "Bearer"), null).get("things", JsonNode.class);

        assertEquals(List.of("GET /api/v1/things null t0k ", "GET /api/v1/things Bearer t0k null "), requests);
    }

    @Test
    void queryParametersAreEncodedAndRepeatedForLists() {
        var params = new LinkedHashMap<String, Object>();
        params.put("q", "a b");
        params.put("tag", List.of("x", "y"));
        params.put("skipped", null);

        http(null, null).get("things", params, JsonNode.class);

        assertEquals("GET /api/v1/things?q=a+b&tag=x&tag=y null null ", requests.getFirst());
    }

    @Test
    void anErrorStatusThrowsWithTheStatusAndBody() {
        status = 403;
        var error = assertThrows(ServiceClientException.class, () -> http(null, null).get("things", JsonNode.class));
        assertEquals(403, error.status());
        assertEquals("nope", error.body());
        assertFalse(error.retryable());
    }

    @Test
    void findTreatsNotFoundAsEmpty() {
        status = 404;
        assertTrue(http(null, null).find("things/1", Map.of(), Count.class).isEmpty());
    }

    @Test
    void serverErrorsAreRetriedUpToMaxRetries() {
        failuresRemaining.set(2);
        assertEquals(2, http(null, 2).get("things", Count.class).count());
        assertEquals(3, requests.size());

        requests.clear();
        failuresRemaining.set(5);
        var error = assertThrows(ServiceClientException.class, () -> http(null, 1).get("things", Count.class));
        assertEquals(503, error.status());
        assertTrue(error.retryable());
        assertEquals(2, requests.size());
    }

    @Test
    void aPostIsNotSentAgainAfterAServerError() {
        failuresRemaining.set(5);
        var error = assertThrows(ServiceClientException.class,
                () -> http(null, 3).post("things", Map.of("a", 1), Count.class));
        assertEquals(503, error.status());
        assertEquals(1, requests.size());
    }

    @Test
    void clientErrorsAreNotRetried() {
        status = 400;
        assertThrows(ServiceClientException.class, () -> http(null, 3).get("things", Count.class));
        assertEquals(1, requests.size());
    }

    @Test
    void headerNamesAreCaseInsensitive() {
        var headers = http(null, null).request("GET", "things", null).headers();
        assertEquals("t1", headers.get("X-Trace-Id"));
        assertEquals("t1", headers.get("x-trace-id"));
    }

    @Test
    void aLongErrorBodyIsShortenedInTheMessageOnly() {
        status = 502;
        errorBody = "x".repeat(5000);
        var error = assertThrows(ServiceClientException.class, () -> http(null, null).get("things", Count.class));
        assertEquals(5000, error.body().length());
        assertTrue(error.getMessage().length() < 1200, error.getMessage());
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
        var client = new ServiceClient(new ServiceClientOptions.WsProxy(proxy.adapter, "WorkflowEngineService",
                "s:wfe1", "ar-123"));

        var count = client.post("/api/v1/workflows", Map.of("name", "x"), Count.class);

        assertEquals(7, count.count());
        var sent = proxy.sent.getFirst();
        assertEquals("s:wfe1", sent.id());
        assertEquals("ar-123", sent.authRef());
        assertEquals("POST", sent.request().method());
        assertEquals("/api/v1/workflows", sent.request().path());
        assertEquals("{\"name\":\"x\"}",
                new String(Base64.getDecoder().decode(sent.request().bodyBase64()), StandardCharsets.UTF_8));
    }

    @Test
    void aHostedBindingKeepsTheStatusOfAProxyError() {
        var proxy = new FakeProxy();
        proxy.status = 404;
        proxy.error = "unknown service instance id \"s:wfe1\"";
        var client = new ServiceClient(new ServiceClientOptions.WsProxy(proxy.adapter, "WorkflowEngineService",
                "s:wfe1", null));

        var error = assertThrows(ServiceClientException.class, () -> client.get("/api/v1/workflows", JsonNode.class));
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
                "WorkflowEngineService", "s:wfe1", null));

        assertThrows(IllegalArgumentException.class, () -> client.post("/things", new Unwritable(), JsonNode.class));
    }

    @Test
    void aHostedCallKeepsTheThreadInterrupted() {
        var client = new ServiceClient(new ServiceClientOptions.WsProxy(new FakeProxy().adapter,
                "WorkflowEngineService", "s:wfe1", null));

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
