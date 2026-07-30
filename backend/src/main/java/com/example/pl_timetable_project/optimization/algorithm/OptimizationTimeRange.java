package com.example.pl_timetable_project.optimization.algorithm;

import java.time.LocalTime;

/** 하루 안에서 수업을 배치할 수 있는 반열린 시간 범위입니다. */
public record OptimizationTimeRange(LocalTime startTime, LocalTime endTime) {

    public boolean contains(CourseTimeSlot slot) {
        return !slot.startTime().isBefore(startTime)
                && !slot.endTime().isAfter(endTime);
    }
}
