package com.example.pl_timetable_project.completedcourse.dto;

import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CompletedCourseUpdateRequest(
        @Size(max = 40) String courseCode,
        @Size(max = 240) String courseName,
        @DecimalMin("0.00") @Digits(integer = 3, fraction = 2) BigDecimal credits,
        @Size(max = 160) String category,
        @Size(max = 120) String area,
        @Size(max = 20) String semester,
        CompletedCourseStatus status) {
}
