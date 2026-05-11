// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSHandleTransaction(
        String transactionId,
        String workflowId,
        String operation,
        Long sequence,
        String idempotencyKey,
        Integer stackDepth,
        String identity,
        JsonNode identityContext,
        String stage,
        JsonNode state,
        JsonNode queueReduce,
        String authRef,
        String defaultFailureStage,
        JsonNode input,
        String configProfileId,
        JsonNode configProfile,
        List<WSHandleTransactionEvent> events
) {}
