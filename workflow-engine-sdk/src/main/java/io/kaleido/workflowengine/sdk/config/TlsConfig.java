// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.config;

/**
 * PEM-encoded TLS material, shared by the inbound WebSocket server
 * ({@link ServerConfig#tls()}) and the outbound WebSocket client
 * ({@link ClientConfig#tls()}).
 *
 * <p>The file paths are read when the runtime starts, not when this record is
 * constructed.
 *
 * @param enabled                whether to use TLS at all; the other fields are
 *                               ignored when false
 * @param caFile                 CA bundle used to verify the peer. Outbound, omitting
 *                               it falls back to the JDK's default trust store, which
 *                               will not contain a platform-internal CA
 * @param certFile               this side's certificate chain. Required inbound;
 *                               outbound it is the client certificate, and is the
 *                               credential the platform authenticates a trusted
 *                               provider by
 * @param keyFile                private key for {@code certFile}. Unencrypted RSA
 *                               only, in either PKCS8 or PKCS1 form
 * @param clientAuth             inbound only: require the connecting peer to present
 *                               a certificate, verified against {@code caFile}
 * @param insecureSkipHostVerify outbound only: accept a certificate whose subject
 *                               does not match the host being dialed
 */
public record TlsConfig(
        boolean enabled,
        String caFile,
        String certFile,
        String keyFile,
        boolean clientAuth,
        boolean insecureSkipHostVerify) {

    /** Enabled inbound server material; {@code insecureSkipHostVerify} does not apply. */
    public static TlsConfig forServer(String caFile, String certFile, String keyFile, boolean clientAuth) {
        return new TlsConfig(true, caFile, certFile, keyFile, clientAuth, false);
    }

    /** Enabled outbound client material; {@code clientAuth} does not apply. */
    public static TlsConfig forClient(String caFile, String certFile, String keyFile, boolean insecureSkipHostVerify) {
        return new TlsConfig(true, caFile, certFile, keyFile, false, insecureSkipHostVerify);
    }

    /** Whether this side has an identity to present, i.e. both a cert and a key. */
    public boolean hasIdentity() {
        return certFile != null && keyFile != null;
    }
}
