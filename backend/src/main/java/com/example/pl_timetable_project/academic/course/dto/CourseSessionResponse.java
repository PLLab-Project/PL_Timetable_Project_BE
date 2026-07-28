package com.example.pl_timetable_project.academic.course.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record CourseSessionResponse(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String roomCode,
        String roomLabel,
        String buildingName,
        List<CourseRoomResponse> rooms) {
}
