package com.example.pl_timetable_project.academic.course;

import java.math.BigDecimal;

public record SectionSearchCondition(
        String semesterId,
        String query,
        String category,
        String academicUnitCode,
        String completionCategory,
        String targetGrade,
        String professor,
        BigDecimal credits,
        String dayCode) {
}
