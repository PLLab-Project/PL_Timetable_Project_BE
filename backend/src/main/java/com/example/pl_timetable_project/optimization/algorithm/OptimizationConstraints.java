package com.example.pl_timetable_project.optimization.algorithm;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record OptimizationConstraints(
        int minCredit,
        int maxCredit,
        int targetCredit,
        Set<DayOfWeek> excludedDays,
        Set<Long> requiredCourseIds,
        LocalTime availableTimeStart,
        LocalTime availableTimeEnd,
        LocalTime lunchTimeStart,
        LocalTime lunchTimeEnd,
        int maxDailyClassMinutes,
        long searchTimeLimitMillis) {
}
