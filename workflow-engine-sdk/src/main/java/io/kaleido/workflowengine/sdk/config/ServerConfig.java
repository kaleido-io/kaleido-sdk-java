// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.config;

/**
 * Inbound-mode server config: the app creates a WebSocket server on
 * {@code address}:{@code port} and the workflow engine connects to it,
 * rather than the app dialing out to a {@code url}.
 *
 * @param port defaults to 6000 when null.
 */
public record ServerConfig(String address, Integer port, TlsConfig tls) {

    public static final int DEFAULT_PORT = 6000;

    public int resolvedPort() {
        return port != null ? port : DEFAULT_PORT;
    }

    public record TlsConfig(boolean enabled, String caFile, String certFile, String keyFile, boolean clientAuth) {
    }
}
