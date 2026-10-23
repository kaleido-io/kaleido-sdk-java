// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import io.kaleido.sdk.core.config.ServiceBindingAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HttpServiceTransportTest {

    record Count(int count) {
    }

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private volatile int status = 200;
    private volatile int failureStatus = 503;
    private volatile String errorBody = "nope";
    private volatile long replyDelayMs;
    private volatile Thread interruptAfterReply;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + exchange.getRequestHeaders().getFirst("X-Api-Key") + " " + body);
            if (replyDelayMs > 0) {
                try {
                    Thread.sleep(replyDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            var code = failuresRemaining.getAndDecrement() > 0 ? failureStatus : status;
            var reply = (code == 200 ? "{\"count\":2}" : errorBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Trace-Id", "t1");
            exchange.getResponseHeaders().add("X-Multi", "a");
            exchange.getResponseHeaders().add("X-Multi", "b");
            exchange.sendResponseHeaders(code, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
            var caller = interruptAfterReply;
            if (caller != null) {
                Thread.ofVirtual().start(() -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                    caller.interrupt();
                });
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private ServiceClient http(ServiceBindingAuth auth, Integer maxRetries) {
        return new ServiceClient(new ServiceClientOptions.Http(base() + "/api/v1/", auth, maxRetries, 5000));
    }

    private static int closedPort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    // ── Auth ────────────────────────────────────────────────────────────────

    @Test
    void basicAuthAndAJsonBody() {
        var count = http(ServiceBindingAuth.basic("user", "pass"), null).post("/things", Map.of("a", 1), Count.class);

        assertEquals(2, count.count());
        var basic = Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("POST /api/v1/things Basic " + basic + " null {\"a\":1}"), requests);
    }

    @Test
    void basicAuthWithoutAPasswordSendsAnEmptyOne() {
        http(ServiceBindingAuth.basic("user", null), null).get("things", JsonNode.class);

        var basic = Base64.getEncoder().encodeToString("user:".getBytes(StandardCharsets.UTF_8));
        assertEquals("GET /api/v1/things Basic " + basic + " null ", requests.getFirst());
    }

    @Test
    void tokenAuthUsesItsHeaderAndScheme() {
        http(ServiceBindingAuth.token("t0k", "X-Api-Key", null), null).get("things", JsonNode.class);
        http(ServiceBindingAuth.token("t0k", null, "Bearer"), null).get("things", JsonNode.class);
        http(ServiceBindingAuth.token("t0k", null, ""), null).get("things", JsonNode.class);

        assertEquals(List.of(
                "GET /api/v1/things null t0k ",
                "GET /api/v1/things Bearer t0k null ",
                "GET /api/v1/things t0k null "), requests);
    }

    @Test
    void noCredentialsSendNoAuthHeader() {
        http(null, null).get("things", JsonNode.class);
        http(new ServiceBindingAuth("token", null, null, null, null, null), null).get("things", JsonNode.class);
        http(new ServiceBindingAuth("basic", null, null, null, null, null), null).get("things", JsonNode.class);

        assertEquals(List.of("GET /api/v1/things null null "), List.copyOf(new java.util.LinkedHashSet<>(requests)));
        assertEquals(3, requests.size());
    }

    // ── URLs, bodies and responses ──────────────────────────────────────────

    @Test
    void theBaseUrlAndPathJoinWithOneSlash() {
        new ServiceClient(new ServiceClientOptions.Http(base() + "/api/v1", null, null, null)).get("things", JsonNode.class);
        new ServiceClient(new ServiceClientOptions.Http(base() + "/api/v1//", null, null, null)).get("//things", JsonNode.class);

        assertEquals(List.of("GET /api/v1/things null null ", "GET /api/v1/things null null "), requests);
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
    void headerNamesAreCaseInsensitiveAndRepeatedValuesJoined() {
        var headers = http(null, null).request("GET", "things", null).headers();

        assertEquals("t1", headers.get("X-Trace-Id"));
        assertEquals("t1", headers.get("x-trace-id"));
        assertEquals("a, b", headers.get("x-multi"));
    }

    /** A body Jackson cannot write. */
    public static final class Unwritable {
        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void aBodyThatCannotBeWrittenIsTheCallersErrorAndNothingIsSent() {
        assertThrows(IllegalArgumentException.class, () -> http(null, 3).post("things", new Unwritable(), JsonNode.class));
        assertTrue(requests.isEmpty());
    }

    // ── Errors ──────────────────────────────────────────────────────────────

    @Test
    void anErrorStatusThrowsWithTheStatusAndBody() {
        status = 403;
        var error = assertThrows(ServiceClientException.class, () -> http(null, null).get("things", JsonNode.class));

        assertEquals(403, error.status());
        assertEquals("nope", error.body());
        assertFalse(error.retryable());
    }

    @Test
    void aLongErrorBodyIsShortenedInTheMessageOnly() {
        status = 502;
        errorBody = "x".repeat(5000);
        var error = assertThrows(ServiceClientException.class, () -> http(null, null).get("things", Count.class));

        assertEquals(5000, error.body().length());
        assertTrue(error.getMessage().length() < 1200, error.getMessage());
    }

    @Test
    void aSlowServiceTimesOutAsRetryable() {
        replyDelayMs = 1000;
        var client = new ServiceClient(new ServiceClientOptions.Http(base(), null, 0, 100));

        var started = System.nanoTime();
        var error = assertThrows(ServiceClientException.class, () -> client.get("things", Count.class));

        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 900, "timeout was not applied");
        assertEquals(0, error.status());
        assertTrue(error.retryable());
    }

    // ── Retries ─────────────────────────────────────────────────────────────

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
    void withoutMaxRetriesThereIsOneAttempt() {
        failuresRemaining.set(5);
        assertThrows(ServiceClientException.class, () -> http(null, null).get("things", Count.class));
        assertEquals(1, requests.size());
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
    void tooManyRequestsIsRetriedForAnyMethod() {
        failureStatus = 429;
        failuresRemaining.set(1);

        assertEquals(2, http(null, 1).post("things", Map.of("a", 1), Count.class).count());
        assertEquals(2, requests.size());
    }

    @Test
    void clientErrorsAreNotRetried() {
        status = 400;
        assertThrows(ServiceClientException.class, () -> http(null, 3).get("things", Count.class));
        assertEquals(1, requests.size());
    }

    @Test
    void aRefusedConnectionIsRetriedEvenForAPost() throws IOException {
        var client = new ServiceClient(new ServiceClientOptions.Http(
                "http://127.0.0.1:" + closedPort(), null, 1, 1000));

        var started = System.nanoTime();
        var error = assertThrows(ServiceClientException.class, () -> client.post("things", Map.of(), Count.class));

        assertEquals(0, error.status());
        assertTrue(error.retryable());
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() >= 250, "the retry did not wait");
    }

    /** Accepts each connection and closes it without answering. */
    private static ServerSocket hangUp(AtomicInteger connections) throws IOException {
        var socket = new ServerSocket(0);
        Thread.ofVirtual().start(() -> {
            while (!socket.isClosed()) {
                try (var connection = socket.accept()) {
                    connections.incrementAndGet();
                    connection.getInputStream().read(new byte[1024]);
                } catch (IOException e) {
                    return;
                }
            }
        });
        return socket;
    }

    @Test
    void aLostResponseIsRetriedOnlyForIdempotentMethods() throws IOException {
        var connections = new AtomicInteger();
        try (var socket = hangUp(connections)) {
            var url = "http://127.0.0.1:" + socket.getLocalPort();
            var once = new ServiceClient(new ServiceClientOptions.Http(url, null, 0, 1000));
            var client = new ServiceClient(new ServiceClientOptions.Http(url, null, 1, 1000));

            // The JDK client may itself resend an idempotent request on a dropped connection,
            // so compare with one attempt rather than counting connections exactly.
            assertThrows(ServiceClientException.class, () -> once.get("things", Count.class));
            var perAttempt = connections.getAndSet(0);
            assertThrows(ServiceClientException.class, () -> client.get("things", Count.class));
            assertEquals(2 * perAttempt, connections.get());

            connections.set(0);
            var error = assertThrows(ServiceClientException.class, () -> client.patch("things", Map.of(), Count.class));
            assertEquals(1, connections.get());
            assertEquals(0, error.status());
        }
    }

    @Test
    void retryDelaysDoubleUpToFiveSeconds() {
        assertEquals(Duration.ofMillis(500), HttpServiceTransport.nextDelay(Duration.ofMillis(250)));
        assertEquals(Duration.ofSeconds(4), HttpServiceTransport.nextDelay(Duration.ofSeconds(2)));
        assertEquals(Duration.ofSeconds(5), HttpServiceTransport.nextDelay(Duration.ofSeconds(4)));
        assertEquals(Duration.ofSeconds(5), HttpServiceTransport.nextDelay(Duration.ofSeconds(5)));
    }

    // ── Interrupts ──────────────────────────────────────────────────────────

    @Test
    void anInterruptedCallKeepsTheThreadInterrupted() {
        Thread.currentThread().interrupt();
        try {
            var error = assertThrows(ServiceClientException.class, () -> http(null, null).get("things", Count.class));
            assertTrue(error.getMessage().contains("interrupted"), error.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void anInterruptBetweenRetriesStopsRetrying() {
        failuresRemaining.set(5);
        interruptAfterReply = Thread.currentThread();
        try {
            var error = assertThrows(ServiceClientException.class, () -> http(null, 3).get("things", Count.class));
            assertTrue(error.getMessage().contains("interrupted"), error.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, requests.size());
        } finally {
            interruptAfterReply = null;
            Thread.interrupted();
        }
    }
}
