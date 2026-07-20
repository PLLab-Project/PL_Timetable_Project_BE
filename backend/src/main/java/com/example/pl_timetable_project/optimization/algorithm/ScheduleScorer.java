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

/**
 * 완성된 시간표 조합에 점수를 매긴다.
 * 등교일수·공강시간이 적을수록, 점심시간이 확보될수록 가점하고,
 * 목표학점과의 차이 및 하루 최대 수업시간 초과분만큼 감점한다.
 */
@Component
public class ScheduleScorer {

    private static final int DAYS_IN_WEEK = 7;
    private static final double ATTENDANCE_BONUS_PER_DAY = 10.0;
    private static final int GAP_BONUS_CAP_MINUTES = 300;
    private static final double GAP_BONUS_PER_MINUTE = 0.2;
    private static final double LUNCH_BONUS_PER_DAY = 15.0;
    private static final double CREDIT_DIFF_PENALTY_PER_CREDIT = 5.0;
    private static final double DAILY_OVERLOAD_PENALTY_PER_MINUTE = 1.0;

    public ScoredCombination score(ScheduleCombination combination, OptimizationConstraints constraints) {
        Map<DayOfWeek, List<CourseTimeSlot>> slotsByDay = groupSlotsByDay(combination.courses());

        int attendanceDays = slotsByDay.size();
        int totalFreeMinutes = calculateTotalFreeMinutes(slotsByDay);
        int lunchSecuredDays = countLunchSecuredDays(slotsByDay, constraints);
        int dailyOverMinutes = calculateDailyOverMinutes(slotsByDay, constraints.maxDailyClassMinutes());

        double score = 0.0;
        score += (DAYS_IN_WEEK - attendanceDays) * ATTENDANCE_BONUS_PER_DAY;
        score += Math.max(0, GAP_BONUS_CAP_MINUTES - totalFreeMinutes) * GAP_BONUS_PER_MINUTE;
        score += lunchSecuredDays * LUNCH_BONUS_PER_DAY;
        score -= Math.abs(combination.totalCredit() - constraints.targetCredit()) * CREDIT_DIFF_PENALTY_PER_CREDIT;
        score -= dailyOverMinutes * DAILY_OVERLOAD_PENALTY_PER_MINUTE;

        return new ScoredCombination(combination, score, attendanceDays, totalFreeMinutes);
    }

    private Map<DayOfWeek, List<CourseTimeSlot>> groupSlotsByDay(List<CandidateCourse> courses) {
        Map<DayOfWeek, List<CourseTimeSlot>> slotsByDay = new EnumMap<>(DayOfWeek.class);
        for (CandidateCourse course : courses) {
            for (CourseTimeSlot slot : course.timeSlots()) {
                slotsByDay.computeIfAbsent(slot.dayOfWeek(), day -> new ArrayList<>()).add(slot);
            }
        }
        return slotsByDay;
    }

    private int calculateTotalFreeMinutes(Map<DayOfWeek, List<CourseTimeSlot>> slotsByDay) {
        int totalFreeMinutes = 0;
        for (List<CourseTimeSlot> slots : slotsByDay.values()) {
            List<CourseTimeSlot> sorted = slots.stream()
                    .sorted(Comparator.comparing(CourseTimeSlot::startTime))
                    .toList();
            for (int i = 0; i < sorted.size() - 1; i++) {
                LocalTime prevEnd = sorted.get(i).endTime();
                LocalTime nextStart = sorted.get(i + 1).startTime();
                if (prevEnd.isBefore(nextStart)) {
                    totalFreeMinutes += (int) Duration.between(prevEnd, nextStart).toMinutes();
                }
            }
        }
        return totalFreeMinutes;
    }

    private int countLunchSecuredDays(Map<DayOfWeek, List<CourseTimeSlot>> slotsByDay, OptimizationConstraints constraints) {
        int lunchSecuredDays = 0;
        for (List<CourseTimeSlot> slots : slotsByDay.values()) {
            boolean lunchFree = slots.stream().noneMatch(slot -> overlapsLunch(slot, constraints));
            if (lunchFree) {
                lunchSecuredDays++;
            }
        }
        return lunchSecuredDays;
    }

    private boolean overlapsLunch(CourseTimeSlot slot, OptimizationConstraints constraints) {
        return slot.startTime().isBefore(constraints.lunchTimeEnd())
                && constraints.lunchTimeStart().isBefore(slot.endTime());
    }

    private int calculateDailyOverMinutes(Map<DayOfWeek, List<CourseTimeSlot>> slotsByDay, int maxDailyClassMinutes) {
        int totalOverMinutes = 0;
        for (List<CourseTimeSlot> slots : slotsByDay.values()) {
            int dailyMinutes = slots.stream()
                    .mapToInt(slot -> (int) Duration.between(slot.startTime(), slot.endTime()).toMinutes())
                    .sum();
            if (dailyMinutes > maxDailyClassMinutes) {
                totalOverMinutes += dailyMinutes - maxDailyClassMinutes;
            }
        }
        return totalOverMinutes;
    }
}
