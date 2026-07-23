package com.example.pl_timetable_project.academic.section;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AcademicMeeting(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
