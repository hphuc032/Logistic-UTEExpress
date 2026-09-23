package com.uteexpress.identity.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class VerificationMailService {
    private static final String SUBJECT = "UTEExpress - Mã xác minh email";

    private final JavaMailSender mailSender;
    private final VerificationMailProperties properties;

    public VerificationMailService(JavaMailSender mailSender, VerificationMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void send(String recipient, String code, Duration ttl) {
        if (recipient == null || recipient.isBlank()
                || VerificationMailProperties.containsLineBreak(recipient)) {
            throw new IllegalArgumentException("Persisted email address is invalid");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(recipient);
        message.setSubject(SUBJECT);
        message.setText("Mã xác minh UTEExpress của bạn là: " + code + "\n\n"
                + "Mã có hiệu lực trong " + ttl.toMinutes() + " phút.\n"
                + "Không chia sẻ mã này với bất kỳ ai.");
        mailSender.send(message);
    }
}
