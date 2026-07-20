package com.example.pl_timetable_project.optimization.algorithm;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record CourseTimeSlot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {

    public boolean overlaps(CourseTimeSlot other) {
        if (this.dayOfWeek != other.dayOfWeek) {
            return false;
        }
        return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
    }
}
