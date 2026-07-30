package com.example.pl_timetable_project.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** 마이페이지에서 사용하는 회원·학사 통합 정보입니다. */
@Schema(description = "마이페이지에서 사용하는 회원·학생 프로필 통합 정보")
public record UserInfoResponse(
        @Schema(description = "서버 내부 사용자 UUID", example = "2cc6ef64-5f18-4e48-bb97-d77ab7d498e2")
        UUID id,

        @Schema(description = "로그인과 학교 이메일 결정에 사용하는 학번", example = "20201234")
        String studentNumber,

        @Schema(description = "사용자 이름. 아직 입력하지 않았으면 null", example = "홍길동")
        String name,

        @Schema(description = "현재 학년. 아직 입력하지 않았으면 null", example = "3")
        Short grade,

        @Schema(description = "정규화된 학과·전공 코드. 아직 입력하지 않았으면 null", example = "AA0846")
        String departmentId,

        @Schema(description = "학과 코드에 대응하는 현재 표시명", example = "컴퓨터공학과")
        String department,

        @Schema(description = "졸업요건 기준 입학연도", example = "2022")
        Integer admissionYear,

        @Schema(description = "졸업요건 학생 구분", example = "DOMESTIC")
        String studentType,

        @Schema(description = "전공 이수 경로", example = "ADVANCED_MAJOR")
        String programPath,

        @Schema(description = "학년·학과 기본 프로필 입력 완료 여부")
        boolean profileCompleted,

        @Schema(description = "졸업판정에 필요한 필드 입력 완료 여부")
        boolean graduationProfileCompleted,

        @Schema(description = "최초 사용 튜토리얼 완료 여부")
        boolean tutorialCompleted,

        @Schema(description = "회원 레코드 생성 시각", example = "2026-07-25T03:00:00Z")
        Instant createdAt) {
}
