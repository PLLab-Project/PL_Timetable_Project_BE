package com.example.pl_timetable_project.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "academicPrograms 목록에 포함하는 주전공·복수전공·부전공·마이크로전공 한 건")
public record AcademicProgramUpdateRequest(
        @Schema(
                description = "GET /api/v1/departments가 반환한 정규 학과·전공 코드",
                example = "AA0194")
        @NotBlank
        @Size(max = 40)
        String academicUnitCode,

        @Schema(
                description = "학생에게 이 전공이 갖는 역할",
                example = "PRIMARY",
                allowableValues = {"PRIMARY", "DOUBLE_MAJOR", "MINOR", "MICRO_MAJOR"})
        @NotBlank
        @Pattern(regexp = "PRIMARY|DOUBLE_MAJOR|MINOR|MICRO_MAJOR")
        String role) {
}
