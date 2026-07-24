package com.example.pl_timetable_project.academic.semester.dto;

import java.time.Instant;
import java.time.LocalDate;

public record SemesterDataVersionResponse(
        String semesterId,
        String datasetVersion,
        String sourceChecksum,
        LocalDate preparedAt,
        Instant createdAt) {
}
