package com.uteexpress.identity.service;

import com.uteexpress.identity.entity.OtpPurpose;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class OtpSecurityTest {
    private static final String TEST_PEPPER =
            "VVRFRXhwcmVzcy1BVVRILTAzLXRlc3Qtb25seS1wZXBwZXIta2V5IQ==";

    @Test
    void secureGeneratorAlwaysFormatsExactlySixDigitsIncludingLeadingZeros() {
        SecureRandom random = mock(SecureRandom.class);
        given(random.nextInt(1_000_000)).willReturn(4_271, 999_999);
        OtpCodeGenerator generator = new SecureRandomOtpCodeGenerator(random);

        assertThat(generator.generate()).isEqualTo("004271");
        assertThat(generator.generate()).isEqualTo("999999");
    }

    @Test
    void hmacIsDeterministicAndDomainSeparatedWithoutExposingRawCode() {
        OtpHashService hashes = new OtpHashService(properties(TEST_PEPPER));

        String value = hashes.hash(42L, OtpPurpose.EMAIL_VERIFICATION, "123456");

        assertThat(value).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("123456");
        assertThat(hashes.hash(42L, OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isEqualTo(value);
        assertThat(hashes.hash(43L, OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isNotEqualTo(value);
        assertThat(hashes.hash(42L, OtpPurpose.EMAIL_VERIFICATION, "654321"))
                .isNotEqualTo(value);
        assertThat(hashes.hashCanonical(42L, "RESET_PASSWORD", "123456"))
                .isNotEqualTo(value);
        assertThat(hashes.matches(value, 42L, OtpPurpose.EMAIL_VERIFICATION, "123456"))
                .isTrue();
        assertThat(hashes.matches(value, 42L, OtpPurpose.EMAIL_VERIFICATION, "123457"))
                .isFalse();
    }

    @Test
    void invalidOrShortPepperFailsClosed() {
        assertThatThrownBy(() -> properties("not-base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid Base64");
        assertThatThrownBy(() -> properties("dG9vLXNob3J0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    private OtpProperties properties(String pepper) {
        return new OtpProperties(Duration.ofMinutes(10), Duration.ofMinutes(1), 5, pepper);
    }
}
