package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.academic.section.SectionReference;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record OptimizationConstraints(
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
}
