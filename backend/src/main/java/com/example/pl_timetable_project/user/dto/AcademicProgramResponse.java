package com.example.pl_timetable_project.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "학생에게 연결된 주전공·복수전공·부전공·마이크로전공")
public record AcademicProgramResponse(
        UUID id,
        String academicUnitCode,
        String academicUnitName,
        String role,
        String status,
        short displayOrder) {
}
