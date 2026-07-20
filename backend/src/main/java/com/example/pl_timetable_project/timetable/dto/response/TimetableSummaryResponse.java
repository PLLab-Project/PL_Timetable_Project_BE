package com.example.pl_timetable_project.timetable.dto.response;

import com.example.pl_timetable_project.timetable.entity.Timetable;
import lombok.Getter;

@Getter
public class TimetableSummaryResponse {

    private final Long id;
    private final String name;
    private final Integer year;
    private final String semester;
    private final int totalCredits;
    private final int sectionCount;

    private TimetableSummaryResponse(Long id, String name, Integer year, String semester,
                                      int totalCredits, int sectionCount) {
        this.id = id;
        this.name = name;
        this.year = year;
        this.semester = semester;
        this.totalCredits = totalCredits;
        this.sectionCount = sectionCount;
    }

    public static TimetableSummaryResponse of(Timetable timetable, int totalCredits) {
        return new TimetableSummaryResponse(
                timetable.getId(),
                timetable.getName(),
                timetable.getYear(),
                timetable.getSemester(),
                totalCredits,
                timetable.getTimetableCourses().size());
    }
}
