package com.example.pl_timetable_project.timetable.dto.response;

import com.example.pl_timetable_project.timetable.entity.Timetable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

@Getter
public class TimetableResponse {

    private final Long id;
    private final Long userId;
    private final String name;
    private final Integer year;
    private final String semester;
    private final int totalCredits;
    private final List<TimetableCourseResponse> sections;
    private final List<FreeTimeResponse> freeTimes;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private TimetableResponse(Long id, Long userId, String name, Integer year, String semester,
                               int totalCredits, List<TimetableCourseResponse> sections,
                               List<FreeTimeResponse> freeTimes, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.year = year;
        this.semester = semester;
        this.totalCredits = totalCredits;
        this.sections = sections;
        this.freeTimes = freeTimes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static TimetableResponse of(Timetable timetable, int totalCredits,
                                        List<TimetableCourseResponse> sections,
                                        List<FreeTimeResponse> freeTimes) {
        return new TimetableResponse(
                timetable.getId(),
                timetable.getUserId(),
                timetable.getName(),
                timetable.getYear(),
                timetable.getSemester(),
                totalCredits,
                sections,
                freeTimes,
                timetable.getCreatedAt(),
                timetable.getUpdatedAt());
    }
}
