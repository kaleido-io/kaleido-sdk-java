// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEventSourcePollResult(
        WSMessageType messageType,
        String id,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        String checkpoint,
        List<ListenerEvent> events
) {
    public static WSEventSourcePollResult forRequest(WSEventSourcePoll request, String checkpoint, List<ListenerEvent> events) {
        return new WSEventSourcePollResult(
                WSMessageType.EVENT_SOURCE_POLL_RESULT,
                request.id(), null, request.handler(), null, null,
                checkpoint, events);
    }

    public static WSEventSourcePollResult error(WSEventSourcePoll request, String error) {
        return new WSEventSourcePollResult(
                WSMessageType.EVENT_SOURCE_POLL_RESULT,
                request.id(), null, request.handler(), error, null,
                null, null);
    }
}
