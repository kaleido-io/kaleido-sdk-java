// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.sdk.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JSONTest {

    record Item(String id, int count) {
    }

    /** No properties, which Jackson rejects by default. */
    public static final class Empty {
    }

    @Test
    void unknownPropertiesAreIgnored() throws JsonProcessingException {
        assertEquals(new Item("a", 1), JSON.MAPPER.readValue("{\"id\":\"a\",\"count\":1,\"new\":true}", Item.class));
    }

    @Test
    void anEmptyBeanWritesAsAnEmptyObject() throws JsonProcessingException {
        assertEquals("{}", JSON.MAPPER.writeValueAsString(new Empty()));
    }
}
