// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link InboundTls#readPrivateKeyPkcs8Der}'s PEM-label allowlist:
 * the supported key forms load, and every unsupported form fails with an
 * error naming what was found — never a garbled decode of the wrong bytes.
 */
class InboundTlsKeyParsingTest {

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
        assertArrayEquals(pkcs8, InboundTls.readPrivateKeyPkcs8Der(file));
    }

    @Test
    void loadsPkcs1KeyWrappedToPkcs8() throws Exception {
        var file = write("pkcs1.pem", pemBlock("RSA PRIVATE KEY", pkcs1));
        assertArrayEquals(InboundTls.pkcs1RsaToPkcs8(pkcs1), InboundTls.readPrivateKeyPkcs8Der(file));
    }

    @Test
    void skipsNonKeyBlocksInSameFile() throws Exception {
        var file = write("bundle.pem",
                pemBlock("CERTIFICATE", new byte[]{1, 2, 3})
                        + pemBlock("EC PARAMETERS", new byte[]{4, 5})
                        + pemBlock("PRIVATE KEY", pkcs8));
        assertArrayEquals(pkcs8, InboundTls.readPrivateKeyPkcs8Der(file));
    }

    @Test
    void rejectsEcKeyByLabel() throws Exception {
        var file = write("ec.pem", pemBlock("EC PRIVATE KEY", new byte[]{1, 2, 3}));
        var e = assertThrows(IllegalArgumentException.class,
                () -> InboundTls.readPrivateKeyPkcs8Der(file));
        assertTrue(e.getMessage().contains("EC PRIVATE KEY"), e.getMessage());
    }

    @Test
    void rejectsEncryptedPkcs8ByLabel() throws Exception {
        var file = write("enc-pkcs8.pem", pemBlock("ENCRYPTED PRIVATE KEY", new byte[]{1, 2, 3}));
        var e = assertThrows(IllegalArgumentException.class,
                () -> InboundTls.readPrivateKeyPkcs8Der(file));
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
                () -> InboundTls.readPrivateKeyPkcs8Der(file));
        assertTrue(e.getMessage().contains("encrypted RSA PRIVATE KEY"), e.getMessage());
    }

    @Test
    void rejectsFileWithNoKeyBlock() throws Exception {
        var file = write("certs-only.pem", pemBlock("CERTIFICATE", new byte[]{1, 2, 3}));
        var e = assertThrows(IllegalArgumentException.class,
                () -> InboundTls.readPrivateKeyPkcs8Der(file));
        assertTrue(e.getMessage().contains("no private key found"), e.getMessage());
    }
}
