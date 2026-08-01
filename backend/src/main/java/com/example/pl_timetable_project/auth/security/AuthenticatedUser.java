package com.example.pl_timetable_project.auth.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/** 세션에 저장하는 최소 인증 주체입니다. 민감한 사용자 전체 엔티티는 저장하지 않습니다. */
public record AuthenticatedUser(
        UUID userId,
        String studentNumber,
        boolean schoolVerified
) implements Principal, Serializable {

    /** 기존 테스트와 OTP 로그인 호출부는 학번이 있으면 학교 인증된 사용자로 취급합니다. */
    public AuthenticatedUser(UUID userId, String studentNumber) {
        this(userId, studentNumber, studentNumber != null);
    }

    /** Spring Session에는 긴 record 문자열 대신 고정 길이 UUID만 principal_name으로 저장합니다. */
    @Override
    public String getName() {
        return userId.toString();
    }
}
