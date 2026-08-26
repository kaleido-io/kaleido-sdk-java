// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import io.kaleido.workflowengine.sdk.config.ClientConfig;
import io.kaleido.workflowengine.sdk.config.TlsConfig;
import io.kaleido.workflowengine.sdk.protocol.JSON;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-socket test of outbound mode over TLS: the runtime dials a TLS server
 * standing in for the workflow engine. The server demands a client certificate,
 * so a successful connection is itself proof the runtime presented one and
 * verified the server against the configured CA — and the negative cases prove
 * neither half is being silently skipped.
 */
class HandlerRuntimeOutboundTlsTest {

    @TempDir
    static Path certDir;

    private static Path serverCert;
    private static Path serverKey;
    private static Path clientCert;
    private static Path clientKey;

    @BeforeAll
    static void generateCerts() throws Exception {
        // The server SAN names localhost only, so a 127.0.0.1 URL fails hostname
        // verification unless it is explicitly waived.
        var server = selfSigned("server", "CN=localhost", "localhost");
        var client = selfSigned("client", "CN=test-client", null);
        serverCert = server[0];
        serverKey = server[1];
        clientCert = client[0];
        clientKey = client[1];
    }

    /**
     * Mints a self-signed RSA cert and writes it as the cert/key PEM pair the
     * SDK reads. Marked as a CA so the same file serves as its own trust anchor
     * when handed back as {@code caFile}.
     *
     * @param dnsName subject alternative name, or null for no SAN at all
     * @return {@code [certPem, keyPem]}
     */
    private static Path[] selfSigned(String name, String dn, String dnsName) throws Exception {
        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        var keyPair = keyGen.generateKeyPair();

        var subject = new X500Name(dn);
        var now = Instant.now();
        var builder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(1, ChronoUnit.HOURS)),
                Date.from(now.plus(1, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        if (dnsName != null) {
            builder.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName(GeneralName.dNSName, dnsName)));
        }

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        var certPem = certDir.resolve(name + "-cert.pem");
        var keyPem = certDir.resolve(name + "-key.pem");
        Files.writeString(certPem, pem("CERTIFICATE", cert.getEncoded()));
        Files.writeString(keyPem, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        return new Path[]{certPem, keyPem};
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }

    /** A TLS WebSocket server standing in for the engine, demanding a client cert. */
    private static class EngineStub implements AutoCloseable {
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private final CountDownLatch started = new CountDownLatch(1);
        private final WebSocketServer server;

        EngineStub() throws Exception {
            server = new WebSocketServer(new InetSocketAddress("127.0.0.1", 0)) {
                @Override
                public void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake) {
                }

                @Override
                public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {
                }

                @Override
                public void onMessage(org.java_websocket.WebSocket conn, String message) {
                    received.add(message);
                }

                @Override
                public void onError(org.java_websocket.WebSocket conn, Exception ex) {
                }

                @Override
                public void onStart() {
                    started.countDown();
                }
            };
            // Reuse the production inbound factory: the code under test here is
            // the client half, so the server half should be the real thing.
            server.setWebSocketFactory(InboundTls.serverFactory(
                    TlsConfig.forServer(clientCert.toString(), serverCert.toString(), serverKey.toString(), true)));
            server.start();
            assertTrue(started.await(10, TimeUnit.SECONDS), "TLS test server did not start");
        }

        int port() {
            return server.getPort();
        }

        @Override
        public void close() throws Exception {
            server.stop();
        }
    }

    /** Outbound runtime that gives up after one attempt, so failures surface instead of retrying. */
    private static HandlerRuntime outboundRuntime(String url, TlsConfig tls) {
        return new HandlerRuntime(ClientConfig.builder()
                .providerName("outbound-tls-test")
                .wsUrl(URI.create(url))
                .tls(tls)
                .maxAttempts(1)
                .build());
    }

    @Test
    void connectsWithClientCertificate() throws Exception {
        try (var engine = new EngineStub()) {
            var runtime = outboundRuntime("wss://localhost:" + engine.port() + "/ws",
                    TlsConfig.forClient(serverCert.toString(), clientCert.toString(), clientKey.toString(), false));
            runtime.start();
            try {
                var registerMsg = engine.received.poll(10, TimeUnit.SECONDS);
                assertNotNull(registerMsg, "expected register_provider over the mTLS connection");
                var node = JSON.MAPPER.readTree(registerMsg);
                assertEquals("register_provider", node.path("messageType").asText());
                assertEquals("outbound-tls-test", node.path("providerName").asText());
            } finally {
                runtime.stop();
            }
        }
    }

    @Test
    void rejectedWithoutClientCertificate() throws Exception {
        try (var engine = new EngineStub()) {
            var runtime = outboundRuntime("wss://localhost:" + engine.port() + "/ws",
                    TlsConfig.forClient(serverCert.toString(), null, null, false));
            assertThrows(Exception.class, runtime::start,
                    "server demands a client certificate, so a bare TLS client must not connect");
            runtime.stop();
        }
    }

    @Test
    void rejectsServerNotSignedByConfiguredCa() throws Exception {
        try (var engine = new EngineStub()) {
            // Identical to the passing case except that the CA is the client's
            // own cert, which did not sign the server's.
            var runtime = outboundRuntime("wss://localhost:" + engine.port() + "/ws",
                    TlsConfig.forClient(clientCert.toString(), clientCert.toString(), clientKey.toString(), false));
            assertThrows(Exception.class, runtime::start,
                    "caFile must actually be used to verify the server");
            runtime.stop();
        }
    }

    @Test
    void hostnameMismatchNeedsInsecureSkipHostVerify() throws Exception {
        try (var engine = new EngineStub()) {
            // The server cert names localhost, not 127.0.0.1.
            var url = "wss://127.0.0.1:" + engine.port() + "/ws";
            var verifying = outboundRuntime(url,
                    TlsConfig.forClient(serverCert.toString(), clientCert.toString(), clientKey.toString(), false));
            assertThrows(Exception.class, verifying::start, "hostname mismatch must fail by default");
            verifying.stop();

            var waived = outboundRuntime(url,
                    TlsConfig.forClient(serverCert.toString(), clientCert.toString(), clientKey.toString(), true));
            waived.start();
            try {
                assertNotNull(engine.received.poll(10, TimeUnit.SECONDS),
                        "insecureSkipHostVerify should allow the mismatched host");
            } finally {
                waived.stop();
            }
        }
    }

    /**
     * {@code ConfigLoader} rejects this earlier, but a programmatically built
     * config can still reach {@code start()} with no endpoint. It must be named,
     * not passed through to the WebSocket builder as a null.
     */
    @Test
    void outboundWithoutWsUrlIsRejected() {
        var runtime = new HandlerRuntime(ClientConfig.builder()
                .providerName("no-url")
                .maxAttempts(1)
                .build());
        var e = assertThrows(Exception.class, runtime::start);
        assertTrue(e.getMessage().contains("KA140630"), e.getMessage());
    }

    @Test
    void unreadableTlsMaterialFailsStartup() throws Exception {
        var missing = certDir.resolve("does-not-exist.pem");
        var runtime = outboundRuntime("wss://localhost:1/ws",
                TlsConfig.forClient(serverCert.toString(), missing.toString(), missing.toString(), false));
        var e = assertThrows(RuntimeException.class, runtime::start);
        assertTrue(e.getMessage().contains("outbound TLS context"), e.getMessage());
    }
}
