// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Generic envelope used to sniff the {@code messageType} of an incoming message
 * before deserializing to the concrete type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSEnvelope(
        WSMessageType messageType,
        String id,
        String deadline,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        String requestId
) implements WSHandlerEnvelope {
    public static WSEnvelope ofType(WSMessageType type, String id) {
        return new WSEnvelope(type, id, null, null, null, null, null, null);
    }
}
