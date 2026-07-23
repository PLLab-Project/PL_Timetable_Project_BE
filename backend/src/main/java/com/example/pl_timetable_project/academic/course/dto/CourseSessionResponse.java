package com.example.pl_timetable_project.academic.course.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record CourseSessionResponse(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        String roomCode,
        String roomLabel,
        String buildingName) {
}
