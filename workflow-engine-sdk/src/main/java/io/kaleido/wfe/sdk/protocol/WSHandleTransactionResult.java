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
public record WSHandleTransactionResult(
        String stage,
        String error,
        JsonNode subflow,
        List<PatchOp> stateUpdates,
        List<Trigger> triggers,
        List<HandlerEvent> events,
        String deadline
) {
    public static WSHandleTransactionResult error(String error) {
        return new WSHandleTransactionResult(null, error, null, null, null, null, null);
    }

    public static WSHandleTransactionResult stage(String stage) {
        return new WSHandleTransactionResult(stage, null, null, null, null, null, null);
    }
}
