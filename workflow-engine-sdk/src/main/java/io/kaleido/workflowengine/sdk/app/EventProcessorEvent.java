// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.app;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Event received by an event processor batch function.
 */
public record EventProcessorEvent(
        String idempotencyKey,
        String topic,
        JsonNode data
) {}
