package com.example.pl_timetable_project.optimization.algorithm;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ScheduleScorer {

    private static final int DAYS_IN_WEEK = 7;
    private static final double ATTENDANCE_BONUS_PER_DAY = 10.0;
    private static final int GAP_BONUS_CAP_MINUTES = 300;
    private static final double GAP_BONUS_PER_MINUTE = 0.2;
    private static final double LUNCH_BONUS_PER_DAY = 15.0;
    private static final double CREDIT_DIFF_PENALTY_PER_CREDIT = 5.0;
    private static final double DAILY_OVERLOAD_PENALTY_PER_MINUTE = 1.0;

    public ScoredCombination score(
            ScheduleCombination combination, OptimizationConstraints constraints) {
        Map<DayOfWeek, List<CourseTimeSlot>> slotsByDay =
                groupSlotsByDay(combination.courses());
        int attendanceDays = slotsByDay.size();
        int totalFreeMinutes = calculateTotalFreeMinutes(slotsByDay);
        int lunchSecuredDays = countLunchSecuredDays(slotsByDay, constraints);
        int dailyOverMinutes =
                calculateDailyOverMinutes(slotsByDay, constraints.maxDailyClassMinutes());
        double creditDifference =
                Math.abs(combination.totalCreditUnits() - constraints.targetCreditUnits()) / 100.0;

        double score = 0.0;
        score += (DAYS_IN_WEEK - attendanceDays) * ATTENDANCE_BONUS_PER_DAY;
        score += Math.max(0, GAP_BONUS_CAP_MINUTES - totalFreeMinutes)
                * GAP_BONUS_PER_MINUTE;
        score += lunchSecuredDays * LUNCH_BONUS_PER_DAY;
        score -= creditDifference * CREDIT_DIFF_PENALTY_PER_CREDIT;
        score -= dailyOverMinutes * DAILY_OVERLOAD_PENALTY_PER_MINUTE;
        return new ScoredCombination(combination, score, attendanceDays, totalFreeMinutes);
    }

    private Map<DayOfWeek, List<CourseTimeSlot>> groupSlotsByDay(
            List<CandidateCourse> courses) {
        Map<DayOfWeek, List<CourseTimeSlot>> slotsByDay = new EnumMap<>(DayOfWeek.class);
        for (CandidateCourse course : courses) {
            for (CourseTimeSlot slot : course.timeSlots()) {
                slotsByDay.computeIfAbsent(slot.dayOfWeek(), ignored -> new ArrayList<>())
                        .add(slot);
            }
        }
        return slotsByDay;
    }

    private int calculateTotalFreeMinutes(
            Map<DayOfWeek, List<CourseTimeSlot>> slotsByDay) {
        int totalFreeMinutes = 0;
        for (List<CourseTimeSlot> slots : slotsByDay.values()) {
            List<CourseTimeSlot> sorted = slots.stream()
                    .sorted(Comparator.comparing(CourseTimeSlot::startTime))
                    .toList();
            for (int i = 0; i < sorted.size() - 1; i++) {
                LocalTime previousEnd = sorted.get(i).endTime();
                LocalTime nextStart = sorted.get(i + 1).startTime();
                if (previousEnd.isBefore(nextStart)) {
                    totalFreeMinutes +=
                            (int) Duration.between(previousEnd, nextStart).toMinutes();
                }
            }
        }
        return totalFreeMinutes;
    }

    private int countLunchSecuredDays(
            Map<DayOfWeek, List<CourseTimeSlot>> slotsByDay,
            OptimizationConstraints constraints) {
        int securedDays = 0;
        for (List<CourseTimeSlot> slots : slotsByDay.values()) {
            boolean lunchFree = slots.stream().noneMatch(slot ->
                    slot.startTime().isBefore(constraints.lunchTimeEnd())
                            && constraints.lunchTimeStart().isBefore(slot.endTime()));
            if (lunchFree) {
                securedDays++;
            }
        }
        return securedDays;
    }

    private int calculateDailyOverMinutes(
            Map<DayOfWeek, List<CourseTimeSlot>> slotsByDay,
            int maxDailyClassMinutes) {
        int totalOverMinutes = 0;
        for (List<CourseTimeSlot> slots : slotsByDay.values()) {
            int dailyMinutes = slots.stream()
                    .mapToInt(slot -> (int) Duration.between(
                            slot.startTime(), slot.endTime()).toMinutes())
                    .sum();
            totalOverMinutes += Math.max(0, dailyMinutes - maxDailyClassMinutes);
        }
        return totalOverMinutes;
    }
}
