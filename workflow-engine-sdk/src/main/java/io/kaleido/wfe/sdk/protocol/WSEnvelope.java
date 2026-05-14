// Copyright © 2025 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Shared envelope fields for every protocol message.
 *
 * <p>{@link #authTokens()} carry per-{@code authRef} tokens propagated by the
 * engine's {@code AuthProvider}. <strong>Treat these as untrusted unless the
 * connection is bounded by mTLS</strong> -- the Go SDK's
 * {@code kldplugins/auth_provider.go} flags in-band token forwarding to
 * third-party providers as a migration-compat feature. If you have not
 * pinned the engine's identity via {@code requiredDNAttributes}, do not use
 * these values to make authorisation decisions.
 *
 * <p>See {@code .cursor/plans/go-sdk.md} for the protocol source of truth.
 */
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
