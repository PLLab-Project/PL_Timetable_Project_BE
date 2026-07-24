package com.example.pl_timetable_project.completedcourse.dto;

import java.math.BigDecimal;
import java.util.Map;

public record CompletedCourseCreditSummaryResponse(
        BigDecimal totalCredits,
        BigDecimal completedCredits,
        BigDecimal inProgressCredits,
        Map<String, BigDecimal> creditsByCategory,
        Map<String, BigDecimal> creditsByArea,
        Map<String, BigDecimal> creditsByStatus) {
}
