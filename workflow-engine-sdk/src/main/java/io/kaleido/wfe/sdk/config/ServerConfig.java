// Copyright (c) 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.wfe.sdk.config;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration for server mode (engine dials into the SDK).
 *
 * <p>Mirrors the Go SDK's {@code enginesdk/config.go} server section. The WebSocket
 * upgrade path is hard-coded to {@code /ws} (see {@code NewHandlerRuntimeServerWrapper}
 * in {@code workflow-engine/pkg/enginesdk/handler_runtime.go}); the engine dials that
 * exact path, so it isn't configurable here.
 *
 * <p>There is no bearer-token / header auth on the WS upgrade. The supported identity
 * boundary is mTLS via {@link TlsConfig#clientAuth()} + {@link TlsConfig#requiredDNAttributes()}.
 * See {@code .cursor/plans/go-sdk.md} for the protocol source of truth.
 */
public record ServerConfig(
        String address,
        int port,
        Duration heartbeatInterval,
        int requestsPerSecond,
        int burst,
        int readBufferSize,
        int writeBufferSize,
        TlsConfig tls
) {
    public ServerConfig {
        if (address == null || address.isBlank()) address = "0.0.0.0";
        if (heartbeatInterval == null) heartbeatInterval = Duration.ofSeconds(15);
    }

    public ServerConfig(String address, int port) {
        this(address, port, Duration.ofSeconds(15), 0, 0, 0, 0, null);
    }

    /**
     * TLS configuration for the inbound listener.
     *
     * @param enabled true to terminate TLS on the listener
     * @param certFile path to the server certificate (PEM)
     * @param keyFile path to the server private key (PEM)
     * @param caFile optional CA bundle for verifying client certs (mTLS)
     * @param clientAuth true to require and verify a client certificate (mTLS)
     * @param requiredDNAttributes optional map of subject DN attributes the client cert must contain
     */
    public record TlsConfig(
            boolean enabled,
            String certFile,
            String keyFile,
            String caFile,
            boolean clientAuth,
            Map<String, String> requiredDNAttributes
    ) {
        public TlsConfig(boolean enabled, String certFile, String keyFile) {
            this(enabled, certFile, keyFile, null, false, null);
        }
    }
}
