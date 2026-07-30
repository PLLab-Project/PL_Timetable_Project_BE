package com.example.pl_timetable_project.academic.course;

import java.math.BigDecimal;
import java.util.List;

public record CourseSearchCondition(
        String semesterId,
        String query,
        List<String> categories,
        List<String> academicUnitCodes,
        List<String> collegeCodes,
        String professor,
        BigDecimal credits,
        String dayCode) {
}
