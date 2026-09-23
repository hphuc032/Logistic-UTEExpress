package com.uteexpress.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VerificationMailServiceTest {
    @Test
    void sendsServerControlledPlainTextMessageToPersistedRecipient() {
        JavaMailSender sender = mock(JavaMailSender.class);
        VerificationMailService service = new VerificationMailService(
                sender, new VerificationMailProperties("no-reply@uteexpress.test"));

        service.send("persisted@example.com", "004271", Duration.ofMinutes(10));

        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@uteexpress.test");
        assertThat(message.getTo()).containsExactly("persisted@example.com");
        assertThat(message.getSubject()).isEqualTo("UTEExpress - Mã xác minh email");
        assertThat(message.getText())
                .contains("004271", "10 phút", "Không chia sẻ")
                .doesNotContain("password", "JWT", "tokenVersion");
    }

    @Test
    void rejectsRecipientOrSenderHeaderInjection() {
        JavaMailSender sender = mock(JavaMailSender.class);
        VerificationMailService service = new VerificationMailService(
                sender, new VerificationMailProperties("no-reply@uteexpress.test"));

        assertThatThrownBy(() -> service.send(
                "victim@example.com\r\nBcc: attacker@example.com", "123456", Duration.ofMinutes(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerificationMailProperties(
                "no-reply@example.com\nBcc: attacker@example.com"))
                .isInstanceOf(IllegalStateException.class);
        verify(sender, org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));
    }
}
