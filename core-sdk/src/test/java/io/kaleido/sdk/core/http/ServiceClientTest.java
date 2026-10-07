// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ServiceClient} on its own, over a transport that records requests and answers from a queue. */
class ServiceClientTest {

    record Count(int count) {
    }

    private final List<String> sent = new ArrayList<>();
    private final List<ServiceResponse> replies = new ArrayList<>();

    private ServiceClient client(ServiceResponse... responses) {
        replies.addAll(Arrays.asList(responses));
        return new ServiceClient((method, path, body) -> {
            sent.add(method + " " + path + " " + body);
            return replies.removeFirst();
        });
    }

    private static ServiceResponse reply(int status, String body) {
        return new ServiceResponse(status, Map.of(), body.getBytes(StandardCharsets.UTF_8));
    }

    private static final ServiceResponse COUNT = reply(200, "{\"count\":3}");

    @Test
    void eachHelperSendsItsMethodAndReadsTheResponse() {
        var client = client(COUNT, COUNT, COUNT, COUNT, COUNT, reply(204, ""));

        assertEquals(3, client.get("/a", Count.class).count());
        assertEquals(3, client.get("/a", Map.of("q", 1), Count.class).count());
        assertEquals(3, client.post("/a", Map.of("x", 1), Count.class).count());
        assertEquals(3, client.put("/a", Map.of("x", 2), Count.class).count());
        assertEquals(3, client.patch("/a", Map.of("x", 3), Count.class).count());
        client.delete("/a/1");

        assertEquals(List.of("GET /a null", "GET /a?q=1 null", "POST /a {x=1}", "PUT /a {x=2}", "PATCH /a {x=3}",
                "DELETE /a/1 null"), sent);
    }

    @Test
    void anEmptyBodyOrVoidTypeReadsAsNull() {
        var client = client(reply(204, ""), COUNT);

        assertNull(client.post("/a", null, Count.class));
        assertNull(client.post("/a", null, Void.class));
    }

    @Test
    void requestReturnsTheRawResponse() {
        var response = client(reply(201, "created")).request("OPTIONS", "/a", null);

        assertEquals(201, response.status());
        assertEquals("created", response.text());
    }

    @Test
    void onlyTwoHundredsAreSuccess() {
        for (var status : List.of(100, 199, 300, 304, 400, 500)) {
            var error = assertThrows(ServiceClientException.class,
                    () -> client(reply(status, "e")).get("/a", JsonNode.class), "status " + status);
            assertEquals(status, error.status());
            assertEquals("e", error.body());
            assertEquals("GET /a: HTTP " + status + ": e", error.getMessage());
        }
        for (var status : List.of(200, 204, 299)) {
            assertDoesNotThrow(() -> client(reply(status, "")).get("/a", JsonNode.class), "status " + status);
        }
    }

    @Test
    void aLongErrorBodyIsShortenedInTheMessageOnly() {
        var body = "x".repeat(ServiceClient.MESSAGE_BODY_LIMIT + 1);
        var error = assertThrows(ServiceClientException.class, () -> client(reply(500, body)).get("/a", Count.class));

        assertEquals(body, error.body());
        assertTrue(error.getMessage().endsWith("x".repeat(ServiceClient.MESSAGE_BODY_LIMIT) + "..."));

        var exact = "y".repeat(ServiceClient.MESSAGE_BODY_LIMIT);
        var shown = assertThrows(ServiceClientException.class, () -> client(reply(500, exact)).get("/a", Count.class));
        assertTrue(shown.getMessage().endsWith(": " + exact));
    }

    @Test
    void findReadsNotFoundAsEmptyAndAnythingElseAsUsual() {
        var client = client(reply(404, "missing"), COUNT, reply(204, ""), reply(500, "boom"));

        assertTrue(client.find("/a/1", Map.of(), Count.class).isEmpty());
        assertEquals(3, client.find("/a/2", Map.of("v", "x"), Count.class).orElseThrow().count());
        assertTrue(client.find("/a/3", null, Count.class).isEmpty());
        var error = assertThrows(ServiceClientException.class, () -> client.find("/a/4", Map.of(), Count.class));
        assertEquals(500, error.status());

        assertEquals(List.of("GET /a/1 null", "GET /a/2?v=x null", "GET /a/3 null", "GET /a/4 null"), sent);
    }

    @Test
    void queryParametersAreEncodedRepeatedAndAppended() {
        var params = new LinkedHashMap<String, Object>();
        params.put("q", "a b&c");
        params.put("tag", List.of("x", "y"));
        params.put("n", 3);
        params.put("skipped", null);
        params.put("ü", "é");

        assertEquals("/a?q=a+b%26c&tag=x&tag=y&n=3&%C3%BC=%C3%A9", ServiceClient.withQuery("/a", params));
        assertEquals("/a?x=1&q=a+b%26c&tag=x&tag=y&n=3&%C3%BC=%C3%A9", ServiceClient.withQuery("/a?x=1", params));
    }

    @Test
    void noUsableQueryParametersLeaveThePathAlone() {
        var onlyNulls = new HashMap<String, Object>();
        onlyNulls.put("a", null);
        var listOfNulls = new HashMap<String, Object>();
        listOfNulls.put("a", Arrays.asList(null, null));

        assertEquals("/a", ServiceClient.withQuery("/a", null));
        assertEquals("/a", ServiceClient.withQuery("/a", Map.of()));
        assertEquals("/a", ServiceClient.withQuery("/a", onlyNulls));
        assertEquals("/a", ServiceClient.withQuery("/a", listOfNulls));
        assertEquals("/a", ServiceClient.withQuery("/a", Map.of("a", List.of())));
    }

    @Test
    void aTypedClientExtendsIt() {
        class Counter extends ServiceClient {
            Counter(ServiceClientOptions options) {
                super(options);
            }

            int count() {
                return get("/count", Count.class).count();
            }
        }
        ServiceProxy proxy = (serviceType, id, authRef, method, path, headers, body) -> COUNT;

        assertEquals(3, new Counter(new ServiceClientOptions.WsProxy(proxy, "T", "id", null)).count());
    }

    @Test
    void getReadsAGenericType() {
        var counts = client(reply(200, "[{\"count\":1},{\"count\":2}]"))
                .get("/a", Map.of("q", "x"), new TypeReference<List<Count>>() {});

        assertEquals(List.of(new Count(1), new Count(2)), counts);
        assertEquals(List.of("GET /a?q=x null"), sent);
    }
}
