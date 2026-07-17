// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Round-trips a JDK-generated RSA key through {@link InboundTls#pkcs1RsaToPkcs8}
 * to prove the hand-rolled DER wrapping actually reconstructs a loadable,
 * equivalent PKCS8 key from PKCS1 bytes — not just "didn't throw".
 */
class Pkcs1ToPkcs8Test {

    @Test
    void roundTripsRealRsaKey() throws Exception {
        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        var keyPair = keyGen.generateKeyPair();
        var original = (RSAPrivateKey) keyPair.getPrivate();
        var originalPkcs8 = original.getEncoded();

        // A JDK RSA private key's PKCS8 encoding is exactly
        // SEQUENCE { INTEGER version, AlgorithmIdentifier, OCTET STRING pkcs1 } -
        // extract that OCTET STRING's content to get genuine PKCS1 bytes.
        var pkcs1 = extractPkcs1FromPkcs8(originalPkcs8);
        var rewrapped = InboundTls.pkcs1RsaToPkcs8(pkcs1);

        var reloaded = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(rewrapped));

        assertEquals(original.getModulus(), reloaded.getModulus());
        assertEquals(original.getPrivateExponent(), reloaded.getPrivateExponent());
    }

    /**
     * Parses {@code SEQUENCE { INTEGER, SEQUENCE, OCTET STRING }} and returns
     * the OCTET STRING's content bytes (the PKCS1 {@code RSAPrivateKey} DER).
     */
    static byte[] extractPkcs1FromPkcs8(byte[] pkcs8) {
        var offset = new int[]{0};
        descendInto(pkcs8, offset); // outer SEQUENCE - descend into its content
        readTlv(pkcs8, offset); // INTEGER version - skip
        readTlv(pkcs8, offset); // AlgorithmIdentifier SEQUENCE - skip
        return readTlv(pkcs8, offset); // OCTET STRING content == PKCS1 bytes
    }

    /** Advances {@code offset[0]} past a TLV's tag+length, positioning at its content start. */
    private static void descendInto(byte[] data, int[] offset) {
        offset[0]++; // tag
        var lenByte = data[offset[0]] & 0xFF;
        offset[0]++;
        if (lenByte >= 0x80) {
            offset[0] += (lenByte & 0x7F);
        }
    }

    /** Reads one TLV at {@code offset[0]}, advances past it entirely, and returns its content bytes. */
    private static byte[] readTlv(byte[] data, int[] offset) {
        offset[0]++; // tag
        var lenByte = data[offset[0]] & 0xFF;
        offset[0]++;
        int length;
        if (lenByte < 0x80) {
            length = lenByte;
        } else {
            var numBytes = lenByte & 0x7F;
            length = 0;
            for (var i = 0; i < numBytes; i++) {
                length = (length << 8) | (data[offset[0]] & 0xFF);
                offset[0]++;
            }
        }
        var contentStart = offset[0];
        offset[0] += length;
        return Arrays.copyOfRange(data, contentStart, contentStart + length);
    }
}
