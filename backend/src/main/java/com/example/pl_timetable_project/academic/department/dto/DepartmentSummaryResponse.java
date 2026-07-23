package com.example.pl_timetable_project.academic.department.dto;

public record DepartmentSummaryResponse(
        String code,
        String name,
        String collegeCode,
        String collegeName,
        int firstSeenYear,
        int lastSeenYear,
        boolean current) {
}
