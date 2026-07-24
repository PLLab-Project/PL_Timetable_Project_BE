package com.example.pl_timetable_project.completedcourse.dto;

import java.util.List;

public record TimetableImportResponse(
        Long timetableId,
        int importedCount,
        int skippedCount,
        List<CompletedCourseResponse> records) {
}
