package com.example.pl_timetable_project.auth.dto;

import java.util.UUID;

/** 인증 응답에 필요한 최소 사용자 정보만 노출합니다. */
public record AuthUserResponse(UUID id, String studentNumber, String name) {
}
