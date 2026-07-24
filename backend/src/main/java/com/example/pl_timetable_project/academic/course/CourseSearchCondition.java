package com.example.pl_timetable_project.academic.course;

import java.math.BigDecimal;

public record CourseSearchCondition(
        String semesterId,
        String query,
        String category,
        String academicUnitCode,
        String professor,
        BigDecimal credits,
        String dayCode) {
}
