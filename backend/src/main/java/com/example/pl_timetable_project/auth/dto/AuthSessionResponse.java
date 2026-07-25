package com.example.pl_timetable_project.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "현재 브라우저 세션의 로그인 상태")
public record AuthSessionResponse(
        @Schema(description = "유효한 로그인 세션인지 여부", example = "true")
        boolean authenticated,

        @Schema(description = "세션에 연결된 최소 사용자 정보")
        AuthUserResponse user,

        @Schema(description = "서버가 계산한 세션 만료 예정 시각", example = "2026-07-25T13:00:00Z")
        Instant expiresAt
) {
}
