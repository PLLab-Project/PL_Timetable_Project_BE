package com.example.pl_timetable_project.academic.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewCreateRequest(
        @NotBlank @Size(max = 20) String semesterId,
        @NotBlank @Size(max = 40) String courseCode,
        @Size(max = 120) String professor,
        @Min(1) @Max(5) int rating,
        @NotBlank String content) {
}
