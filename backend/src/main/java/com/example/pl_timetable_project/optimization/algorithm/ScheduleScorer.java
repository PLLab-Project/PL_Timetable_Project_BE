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
    /** ScheduleSearchService.SAME_MAJOR_BONUS와 반드시 같은 값으로 맞춘다. */
    private static final double SAME_MAJOR_BONUS = 5.0;
    /** ScheduleSearchService.SEQUENCE_BONUS와 반드시 같은 값으로 맞춘다. */
    private static final double SEQUENCE_BONUS = 5.0;
    /** ScheduleSearchService.GRADUATION_PRIORITY_BONUS와 반드시 같은 값으로 맞춘다. */
    private static final double GRADUATION_PRIORITY_BONUS = 8.0;
    /**
     * ScheduleSearchService.LIBERAL_CREDIT_CAP_PENALTY_PER_CREDIT_UNIT과 같은
     * 비율(크레딧당 5.0)로 맞춘다.
     */
    private static final double LIBERAL_CREDIT_CAP_PENALTY_PER_CREDIT = 5.0;

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
        long sameMajorOptionalCount = combination.courses().stream()
                .filter(course -> !course.required())
                .filter(course -> matchesUserAcademicUnit(course, constraints))
                .count();
        long sequenceBonusOptionalCount = combination.courses().stream()
                .filter(course -> !course.required())
                .filter(course -> matchesSequenceBonus(course, constraints))
                .count();
        long graduationPriorityOptionalCourseCount = combination.courses().stream()
                .filter(course -> !course.required())
                .filter(course -> matchesGraduationPriority(course, constraints))
                .map(course -> course.section().getCourseCode())
                .distinct()
                .count();
        double liberalOverCredits = liberalOverCredits(combination, constraints);

        double score = 0.0;
        score += (DAYS_IN_WEEK - attendanceDays) * ATTENDANCE_BONUS_PER_DAY;
        score += Math.max(0, GAP_BONUS_CAP_MINUTES - totalFreeMinutes)
                * GAP_BONUS_PER_MINUTE;
        score += lunchSecuredDays * LUNCH_BONUS_PER_DAY;
        score -= creditDifference * CREDIT_DIFF_PENALTY_PER_CREDIT;
        score -= dailyOverMinutes * DAILY_OVERLOAD_PENALTY_PER_MINUTE;
        score += sameMajorOptionalCount * SAME_MAJOR_BONUS;
        score += sequenceBonusOptionalCount * SEQUENCE_BONUS;
        score += graduationPriorityOptionalCourseCount * GRADUATION_PRIORITY_BONUS;
        score -= liberalOverCredits * LIBERAL_CREDIT_CAP_PENALTY_PER_CREDIT;
        return new ScoredCombination(combination, score, attendanceDays, totalFreeMinutes);
    }

    /**
     * 교양 학점 상한(liberalCreditCap) 초과분(크레딧 단위)이다. ScheduleSearchService의
     * 소프트 페널티와 동일하게 required 강의도 포함해서 합산한다(교양이면 상한을
     * 이미 소모하므로) — SAME_MAJOR_BONUS/SEQUENCE_BONUS/GRADUATION_PRIORITY_BONUS와
     * 달리 이건 "선택 여부에 대한 선호"가 아니라 "총 교양 학점 소비량"에 대한
     * 페널티라 required를 제외하지 않는다. liberalCreditCap이 null이면(상한 없음)
     * 0이다.
     */
    private double liberalOverCredits(
            ScheduleCombination combination, OptimizationConstraints constraints) {
        if (constraints.liberalCreditCap() == null) {
            return 0.0;
        }
        int liberalCreditUnits = combination.courses().stream()
                .filter(CandidateCourse::liberalCredit)
                .mapToInt(CandidateCourse::creditUnits)
                .sum();
        return Math.max(0, liberalCreditUnits - constraints.liberalCreditCap()) / 100.0;
    }

    /**
     * 강의가 사용자의 학과 코드 목록과 겹치는 학과로 제한돼 있으면(=본인 학과 강의)
     * true. ScheduleSearchService.matchesUserAcademicUnit()과 동일한 판정이다.
     */
    private boolean matchesUserAcademicUnit(
            CandidateCourse course, OptimizationConstraints constraints) {
        if (constraints.userAcademicUnitCodes().isEmpty()
                || course.restrictedAcademicUnitCodes().isEmpty()) {
            return false;
        }
        return course.restrictedAcademicUnitCodes().stream()
                .anyMatch(constraints.userAcademicUnitCodes()::contains);
    }

    /**
     * 강의가 사용자가 선택한 교양 영역에 속하고, 큐레이션된 선수과목 중 하나 이상을
     * 이미 이수했으면 true. ScheduleSearchService.matchesSequenceBonus()와 동일한
     * 판정이다.
     */
    private boolean matchesSequenceBonus(
            CandidateCourse course, OptimizationConstraints constraints) {
        boolean areaSelected = course.liberalAreaCode() != null
                && constraints.selectedLiberalAreas().contains(course.liberalAreaCode());
        if (!areaSelected) {
            return false;
        }
        return course.prerequisiteCourseCodes().stream()
                .anyMatch(constraints.completedCourseCodes()::contains);
    }

    /**
     * ScheduleSearchService.matchesGraduationPriority()와 동일한 판정이다.
     * 과목 단위 distinct는 호출부(score())에서 처리한다.
     */
    private boolean matchesGraduationPriority(
            CandidateCourse course, OptimizationConstraints constraints) {
        return constraints.graduationPriorityCourseCodes()
                .contains(course.section().getCourseCode());
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
