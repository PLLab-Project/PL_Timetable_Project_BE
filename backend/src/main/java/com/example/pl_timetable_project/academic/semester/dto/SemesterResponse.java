package com.example.pl_timetable_project.academic.semester.dto;

import java.time.Instant;
import java.time.LocalDate;

public record SemesterResponse(
        String id,
        LocalDate preparedAt,
        String datasetVersion,
        boolean active,
        Instant createdAt) {
}
