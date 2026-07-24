package com.example.pl_timetable_project.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** null인 필드는 유지하고, 전달된 필드만 수정합니다. */
public record UserUpdateRequest(
        @Size(max = 120) String name,
        @Min(1) @Max(6) Short grade,
        @Size(max = 40) String departmentId
) {
}
