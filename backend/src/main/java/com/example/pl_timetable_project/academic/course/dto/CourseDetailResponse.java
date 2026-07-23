package com.example.pl_timetable_project.academic.course.dto;

import java.math.BigDecimal;
import java.util.List;

public record CourseDetailResponse(
        String semesterId,
        String courseCode,
        String name,
        String category,
        BigDecimal credits,
        int sectionCount,
        BigDecimal ratingAverage,
        long reviewCount,
        BigDecimal bayesianRating,
        List<CourseAcademicUnitResponse> academicUnits) {
}
