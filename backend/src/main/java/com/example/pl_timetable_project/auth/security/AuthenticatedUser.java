package com.example.pl_timetable_project.auth.security;

import java.io.Serializable;
import java.util.UUID;

/** 세션에 저장하는 최소 인증 주체입니다. 민감한 사용자 전체 엔티티는 저장하지 않습니다. */
public record AuthenticatedUser(
        UUID userId,
        String studentNumber,
        boolean schoolVerified
) implements Serializable {

    /** 기존 테스트와 OTP 로그인 호출부는 학번이 있으면 학교 인증된 사용자로 취급합니다. */
    public AuthenticatedUser(UUID userId, String studentNumber) {
        this(userId, studentNumber, studentNumber != null);
    }
}
