// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import io.kaleido.workflowengine.sdk.config.TlsConfig;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Builds an {@link SSLContext} from PEM files.
 *
 * <p>RSA only. If EC or encrypted keys are ever needed, switch to BouncyCastle's
 * {@code PEMParser}/{@code JcaPEMKeyConverter} or similar, rather than extending
 * the manual key handling to more formats.
 */
final class PemTls {

    private PemTls() {
    }

    /**
     * Builds an {@link SSLContext} from {@code tls}.
     *
     * <p>A certificate and key produce key managers, presented as this side's
     * identity — mandatory for a server, and the client certificate for mTLS
     * outbound. A CA file produces trust managers; without one the JDK's default
     * trust store applies, which verifies public CAs but not a platform-internal
     * one. {@code insecureSkipHostVerify} additionally wraps those trust managers
     * to drop the peer-identity check (see {@link #skippingHostnameChecks}).
     *
     * @param direction named in the failure message only, e.g. {@code "inbound"}
     */
    static SSLContext buildSslContext(TlsConfig tls, String direction) {
        try {
            var certFactory = CertificateFactory.getInstance("X.509");

            KeyManager[] keyManagers = null;
            if (tls.hasIdentity()) {
                Certificate[] certChain;
                try (var in = Files.newInputStream(Path.of(tls.certFile()))) {
                    certChain = certFactory.generateCertificates(in).toArray(Certificate[]::new);
                }

                var keyBytes = readPrivateKeyPkcs8Der(Path.of(tls.keyFile()));
                PrivateKey privateKey;
                try {
                    privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
                } catch (InvalidKeySpecException e) {
                    throw new IllegalArgumentException(unsupportedKeyMessage(
                            Path.of(tls.keyFile()), "a non-RSA PRIVATE KEY"), e);
                }

                var keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null, null);
                keyStore.setKeyEntry("identity", privateKey, new char[0], certChain);

                var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, new char[0]);
                keyManagers = kmf.getKeyManagers();
            }

            TrustManager[] trustManagers = null;
            if (tls.caFile() != null) {
                var trustStore = KeyStore.getInstance("JKS");
                trustStore.load(null, null);
                try (var in = Files.newInputStream(Path.of(tls.caFile()))) {
                    var i = 0;
                    for (var ca : certFactory.generateCertificates(in)) {
                        trustStore.setCertificateEntry("ca-" + i++, ca);
                    }
                }
                trustManagers = trustManagers(trustStore);
            }
            if (tls.insecureSkipHostVerify()) {
                trustManagers = skippingHostnameChecks(
                        trustManagers != null ? trustManagers : trustManagers(null));
            }

            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("failed to build " + direction + " TLS context: " + e.getMessage(), e);
        }
    }

    /** Trust managers for {@code trustStore}, or the JDK's defaults when null. */
    private static TrustManager[] trustManagers(KeyStore trustStore) throws Exception {
        var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }

    /**
     * Wraps trust managers so they validate the certificate chain but not the
     * peer's identity.
     *
     * <p>This is the only place the waiver can take effect: {@code HttpClient}
     * sets endpoint identification to HTTPS for every TLS connection and ignores
     * an {@code SSLParameters} that clears it, and the resulting hostname check
     * runs inside the trust manager. Delegating to the two-argument
     * {@code checkServerTrusted} keeps chain validation and drops the identity
     * check, which the engine- and socket-aware overloads would perform.
     */
    private static TrustManager[] skippingHostnameChecks(TrustManager[] delegates) {
        var wrapped = new TrustManager[delegates.length];
        for (var i = 0; i < delegates.length; i++) {
            wrapped[i] = delegates[i] instanceof X509ExtendedTrustManager extended
                    ? new HostnameAgnosticTrustManager(extended)
                    : delegates[i];
        }
        return wrapped;
    }

    private static final class HostnameAgnosticTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager delegate;

        HostnameAgnosticTrustManager(X509ExtendedTrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType, socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType, engine);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }
    }

    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----", Pattern.DOTALL);

    /**
     * Finds the private-key block in a PEM file (which may also carry
     * certificates or {@code EC PARAMETERS} blocks) and returns its DER bytes
     * in PKCS8 form.
     *
     * <p>Accepts an unencrypted RSA key in either PKCS8 ({@code PRIVATE KEY}) or
     * PKCS1 ({@code RSA PRIVATE KEY}) form. The platform's cert-manager issued
     * keys are PKCS1, which Java's {@code KeyFactory} can't load directly, so
     * PKCS1 is wrapped into a PKCS8 {@code PrivateKeyInfo} first.
     */
    static byte[] readPrivateKeyPkcs8Der(Path keyFile) throws IOException {
        var matcher = PEM_BLOCK.matcher(Files.readString(keyFile));
        while (matcher.find()) {
            var label = matcher.group(1);
            var body = matcher.group(2);
            if (!label.endsWith("PRIVATE KEY")) {
                continue;
            }
            return switch (label) {
                case "PRIVATE KEY" -> decodePemBody(body);
                case "RSA PRIVATE KEY" -> {
                    // OpenSSL "traditional" encryption keeps the PKCS1 label
                    // and marks encryption with headers inside the block
                    if (body.contains("Proc-Type:") || body.contains("DEK-Info:")) {
                        throw new IllegalArgumentException(
                                unsupportedKeyMessage(keyFile, "an encrypted RSA PRIVATE KEY"));
                    }
                    yield pkcs1RsaToPkcs8(decodePemBody(body));
                }
                default -> throw new IllegalArgumentException(
                        unsupportedKeyMessage(keyFile, "a " + label));
            };
        }
        throw new IllegalArgumentException("no private key found in " + keyFile);
    }

    private static byte[] decodePemBody(String body) {
        return Base64.getDecoder().decode(body.replaceAll("\\s", ""));
    }

    private static String unsupportedKeyMessage(Path keyFile, String found) {
        return "unsupported private key in " + keyFile + ": found " + found
                + "; only unencrypted RSA keys (PKCS1 or PKCS8) are supported";
    }

    /** SEQUENCE { OID 1.2.840.113549.1.1.1 (rsaEncryption), NULL } — fixed DER encoding. */
    private static final byte[] RSA_ALGORITHM_IDENTIFIER = {
            0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
            (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
    };

    /**
     * Wraps a PKCS1 RSA private key (raw {@code RSAPrivateKey} DER) into a
     * PKCS8 {@code PrivateKeyInfo} structure, the shape {@code KeyFactory}
     * requires: {@code SEQUENCE { version=0, AlgorithmIdentifier, OCTET STRING pkcs1 } }.
     */
    static byte[] pkcs1RsaToPkcs8(byte[] pkcs1) {
        var version = new byte[]{0x02, 0x01, 0x00};
        var octetString = derWrap(0x04, pkcs1);
        var body = concat(version, RSA_ALGORITHM_IDENTIFIER, octetString);
        return derWrap(0x30, body);
    }

    private static byte[] derWrap(int tag, byte[] content) {
        var length = derLength(content.length);
        var result = new byte[1 + length.length + content.length];
        result[0] = (byte) tag;
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(content, 0, result, 1 + length.length, content.length);
        return result;
    }

    private static byte[] derLength(int len) {
        if (len < 0x80) {
            return new byte[]{(byte) len};
        }
        var be = new ArrayList<Byte>();
        var n = len;
        while (n > 0) {
            be.add(0, (byte) (n & 0xFF));
            n >>>= 8;
        }
        var result = new byte[1 + be.size()];
        result[0] = (byte) (0x80 | be.size());
        for (var i = 0; i < be.size(); i++) {
            result[1 + i] = be.get(i);
        }
        return result;
    }

    private static byte[] concat(byte[]... arrays) {
        var total = 0;
        for (var a : arrays) {
            total += a.length;
        }
        var result = new byte[total];
        var pos = 0;
        for (var a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}
