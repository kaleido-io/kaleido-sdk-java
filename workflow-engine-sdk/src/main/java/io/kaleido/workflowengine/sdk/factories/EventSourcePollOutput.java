// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.factories;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaleido.workflowengine.sdk.protocol.JSON;

import java.util.List;

/**
 * Result of an event source poll: the new checkpoint plus the events read
 * since the previous checkpoint.
 */
public record EventSourcePollOutput(
        JsonNode checkpoint,
        List<EventSourceEvent> events
) {
    public static EventSourcePollOutput of(Object checkpoint, List<EventSourceEvent> events) {
        return new EventSourcePollOutput(JSON.MAPPER.valueToTree(checkpoint), events);
    }
}
