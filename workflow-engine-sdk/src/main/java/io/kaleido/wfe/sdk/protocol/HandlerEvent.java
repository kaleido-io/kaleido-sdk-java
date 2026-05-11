// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HandlerEvent(
        String idempotencyKey,
        String topic,
        JsonNode data,
        String transactionId,
        Boolean informational
) {}
