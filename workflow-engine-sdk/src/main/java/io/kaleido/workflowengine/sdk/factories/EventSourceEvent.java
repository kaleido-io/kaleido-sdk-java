// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.protocol.JSON;

/**
 * Event returned by an event source poll function.
 */
public record EventSourceEvent(
        String idempotencyKey,
        String topic,
        JsonNode data
) {
    public static EventSourceEvent of(String idempotencyKey, String topic, Object data) {
        return new EventSourceEvent(idempotencyKey, topic, JSON.MAPPER.valueToTree(data));
    }
}
