// Copyright © 2026 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package io.kaleido.workflowengine.sdk.runtime;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves {@link PemTls#pkcs1RsaToPkcs8}'s hand-rolled DER wrapping reconstructs
 * a loadable, equivalent PKCS8 key from PKCS1 bytes — not just "didn't throw".
 *
 * <p>Checked two ways, because they make different claims: the JDK's
 * {@code KeyFactory} accepting the result is what production actually depends
 * on, while an ASN.1 parse confirms the bytes are structurally what was
 * intended.
 */
class Pkcs1ToPkcs8Test {

    private static RSAPrivateCrtKey original;
    private static byte[] pkcs1;

    @BeforeAll
    static void generateKey() throws Exception {
        var keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        original = (RSAPrivateCrtKey) keyGen.generateKeyPair().getPrivate();
        pkcs1 = extractPkcs1FromPkcs8(original.getEncoded());
    }

    @Test
    void roundTripsRealRsaKey() throws Exception {
        var rewrapped = PemTls.pkcs1RsaToPkcs8(pkcs1);

        var reloaded = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(rewrapped));

        assertEquals(original.getModulus(), reloaded.getModulus());
        assertEquals(original.getPrivateExponent(), reloaded.getPrivateExponent());
    }

    @Test
    void wrapIsStructurallyValidPkcs8() throws Exception {
        var wrapped = PemTls.pkcs1RsaToPkcs8(pkcs1);

        var info = PrivateKeyInfo.getInstance(wrapped);
        assertEquals(PKCSObjectIdentifiers.rsaEncryption, info.getPrivateKeyAlgorithm().getAlgorithm());
        assertEquals(0, info.getVersion().intValueExact(), "PKCS8 PrivateKeyInfo version must be 0");

        var parsed = org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(info.parsePrivateKey());
        assertEquals(original.getModulus(), parsed.getModulus());
        assertEquals(original.getPrivateExponent(), parsed.getPrivateExponent());
        assertEquals(original.getPublicExponent(), parsed.getPublicExponent());
        // The CRT coefficient is the last field in the structure, so a length
        // error would corrupt it while leaving the leading fields intact.
        assertEquals(original.getCrtCoefficient(), parsed.getCoefficient());
    }

    /**
     * Exercises {@code derLength}'s short-form/long-form boundary, which a real
     * 2048-bit key (~1190 bytes, always long form) never reaches. The payloads
     * are not valid keys — only the DER framing around them is under test.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 127, 128, 129, 255, 256, 1024})
    void wrapsPayloadsAcrossDerLengthBoundaries(int payloadSize) {
        var payload = new byte[payloadSize];
        for (var i = 0; i < payloadSize; i++) {
            payload[i] = (byte) (i % 251);
        }

        var wrapped = PemTls.pkcs1RsaToPkcs8(payload);

        var sequence = ASN1Sequence.getInstance(wrapped);
        assertEquals(3, sequence.size(), "expected SEQUENCE { version, AlgorithmIdentifier, OCTET STRING }");
        assertArrayEquals(payload, ASN1OctetString.getInstance(sequence.getObjectAt(2)).getOctets());
    }

    /**
     * Returns the PKCS1 {@code RSAPrivateKey} DER carried inside a PKCS8
     * encoding, which is what a JDK RSA private key's {@code getEncoded()}
     * wraps. Shared with {@link PemTlsKeyParsingTest}.
     */
    static byte[] extractPkcs1FromPkcs8(byte[] pkcs8) throws Exception {
        return PrivateKeyInfo.getInstance(pkcs8).parsePrivateKey().toASN1Primitive().getEncoded("DER");
    }
}
