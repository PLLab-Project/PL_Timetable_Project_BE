package com.example.pl_timetable_project.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Google 로그인 계정의 학교 이메일 OTP 인증 결과")
public record SchoolVerificationResponse(
        @Schema(description = "학교 인증 완료 여부", example = "true")
        boolean verified,

        @Schema(description = "인증된 학번", example = "20261234")
        String studentNumber,

        @Schema(description = "학교 인증 완료 시각")
        Instant verifiedAt
) {
}
