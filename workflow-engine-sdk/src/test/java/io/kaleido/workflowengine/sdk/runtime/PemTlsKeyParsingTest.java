// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link PemTls#readPrivateKeyPkcs8Der}'s PEM-label allowlist:
 * the supported key forms load, and every unsupported form fails with an
 * error naming what was found — never a garbled decode of the wrong bytes.
 */
class PemTlsKeyParsingTest {

    @TempDir
    static Path tempDir;

    private static byte[] pkcs8;
    private static byte[] pkcs1;

    @BeforeAll
    static void generateKey() throws Exception {
        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        pkcs8 = keyGen.generateKeyPair().getPrivate().getEncoded();
        pkcs1 = Pkcs1ToPkcs8Test.extractPkcs1FromPkcs8(pkcs8);
    }

    private static String pemBlock(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }

    private static Path write(String name, String content) throws Exception {
        var file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Test
    void loadsPkcs8Key() throws Exception {
        var file = write("pkcs8.pem", pemBlock("PRIVATE KEY", pkcs8));
        assertArrayEquals(pkcs8, PemTls.readPrivateKeyPkcs8Der(file));
    }

    /**
     * Reads a PKCS1 PEM produced by an independent writer, which is the form
     * OpenSSL and the platform's cert-manager emit. The other cases here
     * assemble their PEM blocks from JDK-encoded bytes, so this is the only one
     * whose input the SDK had no hand in producing.
     */
    @Test
    void loadsPkcs1PemWrittenByAnIndependentWriter() throws Exception {
        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        var original = (RSAPrivateKey) keyGen.generateKeyPair().getPrivate();

        var written = new StringWriter();
        try (var pemWriter = new JcaPEMWriter(written)) {
            pemWriter.writeObject(original);
        }
        // Guards the premise: if this writer ever switches to PKCS8, the test
        // silently stops covering the PKCS1 path it exists for.
        assertTrue(written.toString().contains("-----BEGIN RSA PRIVATE KEY-----"),
                "expected a PKCS1 block, got:\n" + written);

        var file = write("independent-pkcs1.pem", written.toString());
        var reloaded = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(PemTls.readPrivateKeyPkcs8Der(file)));

        assertEquals(original.getModulus(), reloaded.getModulus());
        assertEquals(original.getPrivateExponent(), reloaded.getPrivateExponent());
    }

    @Test
    void skipsNonKeyBlocksInSameFile() throws Exception {
        var file = write("bundle.pem",
                pemBlock("CERTIFICATE", new byte[]{1, 2, 3})
                        + pemBlock("EC PARAMETERS", new byte[]{4, 5})
                        + pemBlock("PRIVATE KEY", pkcs8));
        assertArrayEquals(pkcs8, PemTls.readPrivateKeyPkcs8Der(file));
    }

    @Test
    void rejectsEcKeyByLabel() throws Exception {
        var file = write("ec.pem", pemBlock("EC PRIVATE KEY", new byte[]{1, 2, 3}));
        var e = assertThrows(IllegalArgumentException.class,
                () -> PemTls.readPrivateKeyPkcs8Der(file));
        assertTrue(e.getMessage().contains("EC PRIVATE KEY"), e.getMessage());
    }

    @Test
    void rejectsEncryptedPkcs8ByLabel() throws Exception {
        var file = write("enc-pkcs8.pem", pemBlock("ENCRYPTED PRIVATE KEY", new byte[]{1, 2, 3}));
        var e = assertThrows(IllegalArgumentException.class,
                () -> PemTls.readPrivateKeyPkcs8Der(file));
        assertTrue(e.getMessage().contains("ENCRYPTED PRIVATE KEY"), e.getMessage());
    }

    @Test
    void rejectsOpensslEncryptedPkcs1() throws Exception {
        var file = write("enc-pkcs1.pem", "-----BEGIN RSA PRIVATE KEY-----\n"
                + "Proc-Type: 4,ENCRYPTED\n"
                + "DEK-Info: AES-128-CBC,0123456789ABCDEF0123456789ABCDEF\n"
                + "\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pkcs1)
                + "\n-----END RSA PRIVATE KEY-----\n");
        var e = assertThrows(IllegalArgumentException.class,
                () -> PemTls.readPrivateKeyPkcs8Der(file));
        assertTrue(e.getMessage().contains("encrypted RSA PRIVATE KEY"), e.getMessage());
    }

    @Test
    void rejectsFileWithNoKeyBlock() throws Exception {
        var file = write("certs-only.pem", pemBlock("CERTIFICATE", new byte[]{1, 2, 3}));
        var e = assertThrows(IllegalArgumentException.class,
                () -> PemTls.readPrivateKeyPkcs8Der(file));
        assertTrue(e.getMessage().contains("no private key found"), e.getMessage());
    }
}
