package com.example.pl_timetable_project.timetable.dto.response;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import lombok.Getter;

/**
 * 강의와 강의 사이에 비어 있는 공강 시간 구간.
 */
@Getter
public class FreeTimeResponse {

    private final DayOfWeek dayOfWeek;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final long durationMinutes;

    public FreeTimeResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = Duration.between(startTime, endTime).toMinutes();
    }
}
