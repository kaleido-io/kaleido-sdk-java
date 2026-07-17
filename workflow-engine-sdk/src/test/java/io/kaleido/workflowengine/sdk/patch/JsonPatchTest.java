// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.patch;

import io.kaleido.workflowengine.sdk.protocol.JSON;
import io.kaleido.workflowengine.sdk.protocol.PatchOp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonPatchTest {

    private static com.fasterxml.jackson.databind.JsonNode json(String value) throws Exception {
        return JSON.MAPPER.readTree(value);
    }

    @Test
    void emptyPatchReturnsSameState() throws Exception {
        var state = json("{\"a\":1}");
        assertSame(state, JsonPatch.apply(state, null));
        assertSame(state, JsonPatch.apply(state, List.of()));
    }

    @Test
    void addNestedAndArrayAppend() throws Exception {
        var state = json("{\"visits\":{\"one\":[]},\"circuits\":0}");
        var result = JsonPatch.apply(state, List.of(
                PatchOp.add("/visits/one/-", (Object) "first"),
                PatchOp.add("/entered", (Object) true)));
        assertEquals("first", result.at("/visits/one/0").asText());
        assertTrue(result.at("/entered").asBoolean());
    }

    @Test
    void replaceAndRemove() throws Exception {
        var state = json("{\"circuits\":1,\"temp\":\"x\"}");
        var result = JsonPatch.apply(state, List.of(
                PatchOp.replace("/circuits", (Object) 2),
                PatchOp.remove("/temp")));
        assertEquals(2, result.at("/circuits").asInt());
        assertTrue(result.at("/temp").isMissingNode());
    }

    @Test
    void moveAndCopy() throws Exception {
        var state = json("{\"from\":{\"value\":42},\"keep\":\"here\"}");
        var moved = JsonPatch.apply(state, List.of(PatchOp.move("/from/value", "/to")));
        assertEquals(42, moved.at("/to").asInt());
        assertTrue(moved.at("/from/value").isMissingNode());

        var copied = JsonPatch.apply(json("{\"keep\":\"here\"}"),
                List.of(PatchOp.copy("/keep", "/also")));
        assertEquals("here", copied.at("/also").asText());
        assertEquals("here", copied.at("/keep").asText());
    }

    @Test
    void opsApplyInOrder() throws Exception {
        var state = json("{}");
        var result = JsonPatch.apply(state, List.of(
                PatchOp.add("/counter", (Object) 1),
                PatchOp.replace("/counter", (Object) 2)));
        assertEquals(2, result.at("/counter").asInt());
    }

    @Test
    void testOpSuccessAndFailure() throws Exception {
        var state = json("{\"a\":1}");
        var passed = JsonPatch.apply(state, List.of(
                PatchOp.test("/a", json("1")),
                PatchOp.replace("/a", (Object) 2)));
        assertEquals(2, passed.at("/a").asInt());

        assertThrows(Exception.class, () -> JsonPatch.apply(state, List.of(
                PatchOp.test("/a", json("99")),
                PatchOp.replace("/a", (Object) 2))));
    }

    @Test
    void addToMissingParentFails() throws Exception {
        var state = json("{}");
        assertThrows(Exception.class, () ->
                JsonPatch.apply(state, List.of(PatchOp.add("/missing/child", (Object) 1))));
    }
}
