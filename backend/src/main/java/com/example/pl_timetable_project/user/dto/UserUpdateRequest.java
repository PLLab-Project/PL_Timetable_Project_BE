package com.example.pl_timetable_project.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** null인 필드는 유지하고, 전달된 필드만 수정합니다. */
@Schema(description = "내 학생 프로필 부분 수정 요청. 생략하거나 null인 필드는 유지됩니다.")
public record UserUpdateRequest(
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
        String departmentId
) {
}
