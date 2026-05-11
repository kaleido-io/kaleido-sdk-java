// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEnvelope(
        WSMessageType messageType,
        String id,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens
) {
    public static WSEnvelope ofType(WSMessageType type, String id) {
        return new WSEnvelope(type, id, null, null, null, null);
    }

    public static WSEnvelope error(String error) {
        return new WSEnvelope(WSMessageType.PROTOCOL_ERROR, null, null, null, error, null);
    }
}
