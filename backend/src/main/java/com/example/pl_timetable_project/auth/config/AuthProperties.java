package com.example.pl_timetable_project.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.properties의 OTP 정책을 타입 안전하게 읽습니다. */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String schoolEmailDomain, Otp otp) {

    public record Otp(long expirationSeconds, long cooldownSeconds, int maxAttempts) {
    }
}
