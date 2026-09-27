ALTER TABLE uteexpress.otp_tokens
    DROP CONSTRAINT ck_otp_tokens_purpose;

ALTER TABLE uteexpress.otp_tokens
    ADD CONSTRAINT ck_otp_tokens_purpose
        CHECK (purpose IN ('EMAIL_VERIFICATION', 'RESET_PASSWORD'));
