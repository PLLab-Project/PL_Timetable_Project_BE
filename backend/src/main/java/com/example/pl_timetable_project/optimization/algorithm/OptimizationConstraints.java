package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.academic.section.SectionReference;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record OptimizationConstraints(
        int minCreditUnits,
        int maxCreditUnits,
        int targetCreditUnits,
        Set<DayOfWeek> excludedDays,
        Set<SectionReference> requiredSections,
        List<OptimizationTimeRange> availableTimes,
        List<CourseTimeSlot> blockedTimes,
        LocalTime lunchTimeStart,
        LocalTime lunchTimeEnd,
        int maxDailyClassMinutes,
        long searchTimeLimitMillis) {

    public OptimizationConstraints {
        availableTimes = List.copyOf(availableTimes);
        blockedTimes = List.copyOf(blockedTimes);
    }

    public OptimizationConstraints(
            int minCreditUnits,
            int maxCreditUnits,
            int targetCreditUnits,
            Set<DayOfWeek> excludedDays,
            Set<SectionReference> requiredSections,
            LocalTime availableTimeStart,
            LocalTime availableTimeEnd,
            LocalTime lunchTimeStart,
            LocalTime lunchTimeEnd,
            int maxDailyClassMinutes,
            long searchTimeLimitMillis) {
        this(
                minCreditUnits,
                maxCreditUnits,
                targetCreditUnits,
                excludedDays,
                requiredSections,
                List.of(new OptimizationTimeRange(
                        availableTimeStart, availableTimeEnd)),
                List.of(),
                lunchTimeStart,
                lunchTimeEnd,
                maxDailyClassMinutes,
                searchTimeLimitMillis);
    }
}
