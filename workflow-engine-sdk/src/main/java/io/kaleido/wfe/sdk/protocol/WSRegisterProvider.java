// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSRegisterProvider(
        WSMessageType messageType,
        String id,
        WSHandlerType handlerType,
        String handler,
        String error,
        Map<String, String> authTokens,
        String providerName,
        JsonNode providerMetadata
) {
    public static WSRegisterProvider of(String providerName, JsonNode providerMetadata) {
        return new WSRegisterProvider(
                WSMessageType.REGISTER_PROVIDER, null, null, null, null, null,
                providerName, providerMetadata);
    }
}
