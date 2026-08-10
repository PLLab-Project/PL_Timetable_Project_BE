package com.example.pl_timetable_project.academic.course;

import java.math.BigDecimal;
import java.util.List;

public record SectionSearchCondition(
        String semesterId,
        String query,
        List<String> categories,
        List<String> academicUnitCodes,
        List<String> collegeCodes,
        List<String> completionCategories,
        List<String> targetGrades,
        List<String> preferredAcademicUnitCodes,
        String preferredGrade,
        String professor,
        BigDecimal credits,
        String dayCode) {
}
