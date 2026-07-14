// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Provider registration message. {@code capabilities} is omitted when no flags
 * are set so the message stays byte-identical to the pre-1.0 wire format for
 * callers that never declare any capability.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WSRegisterProvider(
        WSMessageType messageType,
        String id,
        String providerName,
        JsonNode providerMetadata,
        ProviderCapabilities capabilities
) {
    public static WSRegisterProvider of(String id, String providerName, JsonNode providerMetadata,
                                        ProviderCapabilities capabilities) {
        return new WSRegisterProvider(
                WSMessageType.REGISTER_PROVIDER, id, providerName, providerMetadata, capabilities);
    }
}
