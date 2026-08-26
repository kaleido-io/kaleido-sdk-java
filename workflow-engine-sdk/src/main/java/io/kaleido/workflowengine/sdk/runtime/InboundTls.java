// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import io.kaleido.workflowengine.sdk.config.TlsConfig;
import org.java_websocket.SSLSocketChannel2;
import org.java_websocket.WebSocketServerFactory;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/** Builds the TLS plumbing for {@link HandlerRuntime}'s inbound WebSocket server. */
final class InboundTls {

    private InboundTls() {
    }

    /** Constructs an SSL factory per {@code tls.clientAuth()}. */
    static WebSocketServerFactory serverFactory(TlsConfig tls) {
        if (tls.clientAuth() && tls.caFile() == null) {
            // Without an explicit CA, client certs would be verified against
            // the JDK's default trust store — never what platform mTLS wants.
            throw new IllegalArgumentException("tls.clientAuth requires tls.caFile to verify client certificates");
        }
        if (!tls.hasIdentity()) {
            throw new IllegalArgumentException("tls requires both tls.certFile and tls.keyFile to serve TLS");
        }
        var sslContext = PemTls.buildSslContext(tls, "inbound");
        return tls.clientAuth()
                ? new ClientAuthSSLWebSocketServerFactory(sslContext)
                : new DefaultSSLWebSocketServerFactory(sslContext);
    }

    /**
     * Requires the connecting client to present a certificate, verified
     * against the SSL context's trust manager — {@code DefaultSSLWebSocketServerFactory}
     * builds server-auth-only engines, with no hook to flip this, so
     * {@code wrapChannel} is reimplemented here to add
     * {@code setNeedClientAuth(true)} before handing the engine off.
     *
     * <p>Everything except the {@code setNeedClientAuth} call must stay in
     * lockstep with {@code DefaultSSLWebSocketServerFactory.wrapChannel}, so
     * client-auth and plain servers negotiate TLS identically. (The library's
     * {@code SSLParametersWebSocketServerFactory} is not a substitute: it
     * omits the cipher-suite removal below.)
     */
    private static final class ClientAuthSSLWebSocketServerFactory extends DefaultSSLWebSocketServerFactory {
        ClientAuthSSLWebSocketServerFactory(SSLContext sslContext) {
            super(sslContext);
        }

        @Override
        public ByteChannel wrapChannel(SocketChannel channel, SelectionKey key) throws IOException {
            SSLEngine engine = sslcontext.createSSLEngine();
            // Upstream removes this suite in DefaultSSLWebSocketServerFactory
            // (it breaks the library's SSLSocketChannel2); mirror it exactly.
            var enabledSuites = new ArrayList<>(List.of(engine.getEnabledCipherSuites()));
            enabledSuites.remove("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
            engine.setEnabledCipherSuites(enabledSuites.toArray(new String[0]));
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(true);
            return new SSLSocketChannel2(channel, engine, exec, key);
        }
    }
}
