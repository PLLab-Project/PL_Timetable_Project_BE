package com.example.pl_timetable_project.timetable.dto.response;

import com.example.pl_timetable_project.timetable.entity.TimetableMeeting;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record TimetableMeetingResponse(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime) {

    public static TimetableMeetingResponse from(TimetableMeeting meeting) {
        return new TimetableMeetingResponse(
                meeting.getDayOfWeek(), meeting.getStartTime(), meeting.getEndTime());
    }
}
