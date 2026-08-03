package com.example.pl_timetable_project.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "학생이 이수 중인 전공 한 건")
public record AcademicProgramUpdateRequest(
        @Schema(description = "학과 목록 API가 반환한 정규 코드", example = "AA0846")
        @NotBlank
        @Size(max = 40)
        String academicUnitCode,

        @Schema(
                description = "학생에게 이 전공이 갖는 역할",
                allowableValues = {"PRIMARY", "DOUBLE_MAJOR", "MINOR", "MICRO_MAJOR"})
        @NotBlank
        @Pattern(regexp = "PRIMARY|DOUBLE_MAJOR|MINOR|MICRO_MAJOR")
        String role) {
}
