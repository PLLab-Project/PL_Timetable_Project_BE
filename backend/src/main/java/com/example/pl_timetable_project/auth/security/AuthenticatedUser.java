package com.example.pl_timetable_project.auth.security;

import java.io.Serializable;
import java.util.UUID;

/** 세션에 저장하는 최소 인증 주체입니다. 민감한 사용자 전체 엔티티는 저장하지 않습니다. */
public record AuthenticatedUser(UUID userId, String studentNumber) implements Serializable {
}
