package com.example.pl_timetable_project.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.pl_timetable_project.auth.AuthErrorCode;
import com.example.pl_timetable_project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpOtpSenderTest {

    @Test
    void sendsOtpMailWithConfiguredSender() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpOtpSender sender = new SmtpOtpSender(mailSender, "noreply@example.com");

        sender.send("20260001@daejin.ac.kr", "123456");

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("noreply@example.com");
        assertThat(message.getTo()).containsExactly("20260001@daejin.ac.kr");
        assertThat(message.getText()).contains("123456");
    }

    @Test
    void mapsMailFailureToPublicServiceError() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        SmtpOtpSender sender = new SmtpOtpSender(mailSender, "");

        assertThatThrownBy(() -> sender.send("20260001@daejin.ac.kr", "123456"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(AuthErrorCode.EMAIL_SEND_FAILED));
    }
}
