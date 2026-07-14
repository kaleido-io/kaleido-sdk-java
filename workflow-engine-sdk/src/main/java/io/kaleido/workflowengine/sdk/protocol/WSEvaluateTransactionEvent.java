// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Event attached to an evaluation transaction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WSEvaluateTransactionEvent(
        String topic,
        JsonNode data
) {}
