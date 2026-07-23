package com.example.pl_timetable_project.academic.course.dto;

import java.math.BigDecimal;

public record CourseSummaryResponse(
        String semesterId,
        String courseCode,
        String name,
        String category,
        BigDecimal credits,
        int sectionCount,
        BigDecimal ratingAverage,
        long reviewCount,
        BigDecimal bayesianRating) {
}
