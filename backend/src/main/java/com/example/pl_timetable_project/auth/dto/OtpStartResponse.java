package com.example.pl_timetable_project.auth.dto;

/** OTP 요청 후 프론트가 재전송·만료 타이머를 표시하는 데 사용합니다. */
public record OtpStartResponse(
        String message,
        long cooldownSeconds,
        long expiresInSeconds
) {
}
