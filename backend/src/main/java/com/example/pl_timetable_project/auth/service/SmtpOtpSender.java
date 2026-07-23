package com.example.pl_timetable_project.auth.service;

import com.example.pl_timetable_project.auth.AuthErrorCode;
import com.example.pl_timetable_project.common.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** 운영 환경에서 학교 이메일로 OTP를 전송합니다. */
@Component
@ConditionalOnProperty(name = "app.auth.otp.delivery", havingValue = "smtp")
public class SmtpOtpSender implements OtpSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpOtpSender(JavaMailSender mailSender,
                         @org.springframework.beans.factory.annotation.Value(
                                 "${app.auth.otp.from:${spring.mail.username:}}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(email);
        message.setSubject("[PL Timetable] 로그인 인증번호");
        message.setText("""
                PL Timetable 로그인 인증번호는 %s 입니다.

                인증번호는 잠시 후 만료되며, 본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(code));
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new BusinessException(AuthErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
