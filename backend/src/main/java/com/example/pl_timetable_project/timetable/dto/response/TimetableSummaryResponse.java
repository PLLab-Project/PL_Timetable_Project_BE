package com.example.pl_timetable_project.timetable.dto.response;

import com.example.pl_timetable_project.timetable.entity.Timetable;
import java.math.BigDecimal;

public record TimetableSummaryResponse(
        Long id,
        String name,
        String semesterId,
        BigDecimal totalCredits,
        int sectionCount) {

    public static TimetableSummaryResponse of(Timetable timetable, BigDecimal totalCredits) {
        return new TimetableSummaryResponse(
                timetable.getId(),
                timetable.getName(),
                timetable.getSemesterId(),
                totalCredits,
                timetable.getTimetableCourses().size());
    }
}
