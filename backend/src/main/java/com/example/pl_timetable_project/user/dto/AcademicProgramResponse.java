package com.example.pl_timetable_project.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "학생에게 연결된 주전공·복수전공·부전공·마이크로전공")
public record AcademicProgramResponse(
        @Schema(
                description = "학생 전공 연결 레코드 UUID",
                example = "50db78a7-cf6e-445f-b99c-e4211d8669d6")
        UUID id,

        @Schema(description = "정규 학과·전공 코드", example = "AA0194")
        String academicUnitCode,

        @Schema(description = "학과·전공 현재 표시명", example = "컴퓨터공학전공")
        String academicUnitName,

        @Schema(
                description = "학생에게 이 전공이 갖는 역할",
                example = "PRIMARY",
                allowableValues = {"PRIMARY", "DOUBLE_MAJOR", "MINOR", "MICRO_MAJOR"})
        String role,

        @Schema(
                description = "전공 이수 상태. WITHDRAWN 항목은 응답에서 제외",
                example = "ACTIVE",
                allowableValues = {"PLANNED", "ACTIVE", "COMPLETED"})
        String status,

        @Schema(description = "표시 순서. 주전공은 항상 0", example = "0")
        short displayOrder) {
}
