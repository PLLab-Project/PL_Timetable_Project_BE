package com.example.pl_timetable_project.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * SMTP 연결 전 로컬 개발에서만 사용하는 OTP 전달 구현입니다.
 * 운영 환경에서는 OTP_DELIVERY 값을 변경하고 실제 메일 전송 구현으로 교체해야 합니다.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "app.auth.otp.delivery", havingValue = "console", matchIfMissing = true)
public class ConsoleOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleOtpSender.class);

    @Override
    public void send(String email, String code) {
        // 인증번호 로그는 로컬 개발 편의를 위한 것이며 운영 로그에 남겨서는 안 됩니다.
        log.warn("LOCAL OTP delivery: email={}, code={}", email, code);
    }
}
