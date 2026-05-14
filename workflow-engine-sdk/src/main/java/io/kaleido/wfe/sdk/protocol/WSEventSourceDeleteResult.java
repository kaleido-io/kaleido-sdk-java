// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEventSourceDeleteResult(
        WSMessageType messageType,
        String id,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens
) {
    public static WSEventSourceDeleteResult forRequest(WSEventSourceDelete request) {
        return new WSEventSourceDeleteResult(
                WSMessageType.EVENT_SOURCE_DELETE_RESULT,
                request.id(), null, request.handler(), null, null);
    }

    public static WSEventSourceDeleteResult error(WSEventSourceDelete request, String error) {
        return new WSEventSourceDeleteResult(
                WSMessageType.EVENT_SOURCE_DELETE_RESULT,
                request.id(), null, request.handler(), error, null);
    }
}
