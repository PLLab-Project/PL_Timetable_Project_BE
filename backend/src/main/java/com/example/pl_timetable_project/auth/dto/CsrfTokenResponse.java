package com.example.pl_timetable_project.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 다른 Origin의 SPA도 쿠키를 직접 읽지 않고 CSRF 헤더 값을 받을 수 있게 합니다. */
public record CsrfTokenResponse(
        @Schema(description = "상태 변경 요청에 사용할 헤더 이름", example = "X-XSRF-TOKEN")
        String headerName,
        @Schema(description = "폼 기반 클라이언트용 파라미터 이름", example = "_csrf")
        String parameterName,
        @Schema(description = "현재 브라우저 세션에 연결된 CSRF 토큰")
        String token) {
}
