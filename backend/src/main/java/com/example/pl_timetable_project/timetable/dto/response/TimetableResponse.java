package com.example.pl_timetable_project.timetable.dto.response;

import com.example.pl_timetable_project.timetable.entity.Timetable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TimetableResponse(
        Long id,
        UUID userId,
        String name,
        String semesterId,
        BigDecimal totalCredits,
        List<TimetableCourseResponse> sections,
        List<FreeTimeResponse> freeTimes,
        Instant createdAt,
        Instant updatedAt) {

    public static TimetableResponse of(
            Timetable timetable,
            BigDecimal totalCredits,
            List<TimetableCourseResponse> sections,
            List<FreeTimeResponse> freeTimes) {
        return new TimetableResponse(
                timetable.getId(),
                timetable.getUserId(),
                timetable.getName(),
                timetable.getSemesterId(),
                totalCredits,
                sections,
                freeTimes,
                timetable.getCreatedAt(),
                timetable.getUpdatedAt());
    }
}
