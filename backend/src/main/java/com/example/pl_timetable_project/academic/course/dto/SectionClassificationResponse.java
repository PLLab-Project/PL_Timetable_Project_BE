package com.example.pl_timetable_project.academic.course.dto;

public record SectionClassificationResponse(
        String contextLabel,
        String contextKind,
        String academicUnitCode,
        String completionCategory,
        String targetGrade,
        boolean primary,
        boolean shaded,
        int sourcePage) {
}
