package com.example.pl_timetable_project.academic.review.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        String courseCode,
        String courseName,
        String professor,
        String semester,
        int rating,
        String content,
        Instant createdAt,
        Instant updatedAt) {
}
