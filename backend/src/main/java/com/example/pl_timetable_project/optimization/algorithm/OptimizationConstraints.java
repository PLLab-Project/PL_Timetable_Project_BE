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
        long searchTimeLimitMillis,
        List<String> userAcademicUnitCodes,
        List<String> selectedLiberalAreas,
        Set<String> completedCourseCodes,
        Set<String> graduationPriorityCourseCodes,
        Integer liberalCreditCap) {

    public OptimizationConstraints {
        availableTimes = List.copyOf(availableTimes);
        blockedTimes = List.copyOf(blockedTimes);
        userAcademicUnitCodes = List.copyOf(userAcademicUnitCodes);
        selectedLiberalAreas = List.copyOf(selectedLiberalAreas);
        completedCourseCodes = Set.copyOf(completedCourseCodes);
        graduationPriorityCourseCodes = Set.copyOf(graduationPriorityCourseCodes);
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
                searchTimeLimitMillis,
                List.of(),
                List.of(),
                Set.of(),
                Set.of(),
                null);
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
            long searchTimeLimitMillis,
            List<String> userAcademicUnitCodes) {
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
                searchTimeLimitMillis,
                userAcademicUnitCodes,
                List.of(),
                Set.of(),
                Set.of(),
                null);
    }
}
