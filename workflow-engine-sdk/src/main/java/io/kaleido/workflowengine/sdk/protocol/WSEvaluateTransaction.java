// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Individual evaluation transaction in a {@link WSHandleTransactions} batch,
 * carrying the flow runtime state alongside the mapped input for the stage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEvaluateTransaction(
        String handler,
        String workflowId,
        String transactionId,
        String sequence,
        String idempotencyKey,
        Integer stackDepth,
        String identity,
        JsonNode identityContext,
        String stage,
        JsonNode state,
        JsonNode queueReduce,
        String authRef,
        JsonNode input,
        JsonNode configProfile,
        List<WSEvaluateTransactionEvent> events
) {}
