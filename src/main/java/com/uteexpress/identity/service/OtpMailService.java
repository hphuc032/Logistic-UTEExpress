package com.uteexpress.identity.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class OtpMailService {
    private static final String VERIFICATION_SUBJECT = "UTEExpress - Mã xác minh email";
    private static final String RESET_SUBJECT = "UTEExpress - Mã đặt lại mật khẩu";

    private final JavaMailSender mailSender;
    private final VerificationMailProperties properties;

    public OtpMailService(JavaMailSender mailSender, VerificationMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendEmailVerification(String recipient, String code, Duration ttl) {
        send(recipient, VERIFICATION_SUBJECT,
                "Mã xác minh UTEExpress của bạn là: " + code + "\n\n"
                        + expiryAndSafetyText(ttl));
    }

    public void sendPasswordReset(String recipient, String code, Duration ttl) {
        send(recipient, RESET_SUBJECT,
                "Mã đặt lại mật khẩu UTEExpress của bạn là: " + code + "\n\n"
                        + expiryAndSafetyText(ttl)
                        + "\nNếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
    }

    private void send(String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()
                || VerificationMailProperties.containsLineBreak(recipient)) {
            throw new IllegalArgumentException("Persisted email address is invalid");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private static String expiryAndSafetyText(Duration ttl) {
        return "Mã có hiệu lực trong " + ttl.toMinutes() + " phút.\n"
                + "Không chia sẻ mã này với bất kỳ ai.";
    }
}
