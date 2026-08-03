package com.example.pl_timetable_project.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** null인 필드는 유지하고, 전달된 필드만 수정합니다. */
@Schema(description = "내 학생 프로필 부분 수정 요청. 생략하거나 null인 필드는 유지됩니다.")
public record UserUpdateRequest(
        @Schema(
                description = "사용자 학번. Google 로그인 후 온보딩과 내 정보에서 직접 설정 가능",
                example = "20261234")
        @Pattern(regexp = "\\d{6,20}")
        String studentNumber,

        @Schema(description = "사용자 이름. 최대 120자", example = "홍길동")
        @Size(max = 120)
        String name,

        @Schema(description = "현재 학년. 1~6 범위", example = "3")
        @Min(1)
        @Max(6)
        Short grade,

        @Schema(
                description = "학과 목록 API가 반환한 academic_units 정규 코드",
                example = "AA0846")
        @Size(max = 40)
        String departmentId,

        @Schema(description = "졸업요건 기준 입학연도", example = "2022")
        @Min(1900)
        @Max(2100)
        Integer admissionYear,

        @Schema(description = "졸업요건 학생 구분", example = "DOMESTIC")
        @Size(max = 24)
        String studentType,

        @Schema(
                description = "전공 이수 경로",
                example = "ADVANCED_MAJOR",
                allowableValues = {
                        "ADVANCED_MAJOR", "DOUBLE_MAJOR", "MINOR", "MICRO_MAJOR"
                })
        @Pattern(regexp = "ADVANCED_MAJOR|DOUBLE_MAJOR|MINOR|MICRO_MAJOR")
        String programPath,

        @Schema(description = "최초 사용 튜토리얼 완료 여부", example = "true")
        Boolean tutorialCompleted
) {
}
