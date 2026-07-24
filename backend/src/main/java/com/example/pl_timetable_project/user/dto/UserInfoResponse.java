package com.example.pl_timetable_project.user.dto;

import java.time.Instant;
import java.util.UUID;

/** 마이페이지에서 사용하는 회원·학사 통합 정보입니다. */
public record UserInfoResponse(UUID id, String studentNumber, String name, Short grade,
                               String departmentId, String department, Instant createdAt) {
}
