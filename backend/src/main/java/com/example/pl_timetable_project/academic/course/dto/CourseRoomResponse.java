package com.example.pl_timetable_project.academic.course.dto;

public record CourseRoomResponse(
        int position,
        String roomCode,
        String roomLabel,
        String buildingName) {
}
