// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServiceResponseTest {

    record Item(String id, int count) {
    }

    private static ServiceResponse body(String text) {
        return new ServiceResponse(200, Map.of(), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void missingHeadersAndBodyAreEmpty() {
        var response = new ServiceResponse(204, null, null);

        assertTrue(response.headers().isEmpty());
        assertEquals(0, response.body().length);
        assertEquals("", response.text());
    }

    @Test
    void headersAreCaseInsensitiveAndReadOnly() {
        var response = new ServiceResponse(200, Map.of("Content-Type", "application/json"), null);

        assertEquals("application/json", response.headers().get("content-type"));
        assertEquals("application/json", response.headers().get("CONTENT-TYPE"));
        assertThrows(UnsupportedOperationException.class, () -> response.headers().put("X", "y"));
    }

    @Test
    void theBodyReadsAsTextOrJson() {
        var response = body("{\"id\":\"ü1\",\"count\":2,\"extra\":true}");

        assertEquals("{\"id\":\"ü1\",\"count\":2,\"extra\":true}", response.text());
        assertEquals(new Item("ü1", 2), response.json(Item.class));
        assertEquals(2, response.json(JsonNode.class).get("count").asInt());
        assertEquals(Map.of("id", "ü1", "count", 2, "extra", true), response.json(Map.class));
    }

    @Test
    void anEmptyBodyOrVoidReadsAsNull() {
        assertNull(body("").json(Item.class));
        assertNull(body("{\"id\":\"a\"}").json(Void.class));
    }

    @Test
    void aBodyThatIsNotTheTypeFails() {
        var error = assertThrows(UncheckedIOException.class, () -> body("not json").json(Item.class));
        assertTrue(error.getMessage().contains("not Item JSON"), error.getMessage());
        assertThrows(UncheckedIOException.class, () -> body("[1,2]").json(Item.class));
    }
}
