package com.uteexpress.identity.service;

import com.uteexpress.identity.entity.OtpPurpose;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class OtpHashService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] pepper;

    public OtpHashService(OtpProperties properties) {
        this.pepper = properties.pepperBytes();
    }

    public String hash(Long userId, OtpPurpose purpose, String code) {
        return hashCanonical(userId, purpose.name(), code);
    }

    public boolean matches(String storedHash, Long userId, OtpPurpose purpose, String code) {
        byte[] expected = storedHash.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hash(userId, purpose, code).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    String hashCanonical(Long userId, String purpose, String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            byte[] value = (userId + ":" + purpose + ":" + code)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
