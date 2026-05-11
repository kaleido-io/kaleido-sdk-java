// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEventProcessorBatchResult(
        WSMessageType messageType,
        String id,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        String checkpoint,
        List<ListenerEvent> events
) {
    public static WSEventProcessorBatchResult forRequest(WSEventProcessorBatchRequest request) {
        return new WSEventProcessorBatchResult(
                WSMessageType.EVENT_PROCESSOR_BATCH_RESULT,
                request.id(), null, request.handler(), null, null,
                null, null);
    }

    public static WSEventProcessorBatchResult error(WSEventProcessorBatchRequest request, String error) {
        return new WSEventProcessorBatchResult(
                WSMessageType.EVENT_PROCESSOR_BATCH_RESULT,
                request.id(), null, request.handler(), error, null,
                null, null);
    }
}
