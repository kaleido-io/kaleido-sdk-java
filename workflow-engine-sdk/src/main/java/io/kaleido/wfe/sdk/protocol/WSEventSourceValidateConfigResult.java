// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEventSourceValidateConfigResult(
        WSMessageType messageType,
        String id,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        JsonNode config
) {
    public static WSEventSourceValidateConfigResult forRequest(WSEventSourceValidateConfig request, JsonNode config) {
        return new WSEventSourceValidateConfigResult(
                WSMessageType.EVENT_SOURCE_VALIDATE_CONFIG_RESULT,
                request.id(), null, request.handler(), null, null, config);
    }

    public static WSEventSourceValidateConfigResult error(WSEventSourceValidateConfig request, String error) {
        return new WSEventSourceValidateConfigResult(
                WSMessageType.EVENT_SOURCE_VALIDATE_CONFIG_RESULT,
                request.id(), null, request.handler(), error, null, null);
    }
}
