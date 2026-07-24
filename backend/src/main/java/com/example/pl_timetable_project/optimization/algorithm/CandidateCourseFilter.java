package com.example.pl_timetable_project.optimization.algorithm;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 제외 요일에 해당하거나 수업 가능 시간대를 벗어나는 슬롯을 가진 강의를 후보에서 제거한다.
 * 강의의 슬롯 중 하나라도 조건을 위반하면 그 강의 전체를 후보에서 제외한다.
 */
@Component
public class CandidateCourseFilter {

    public List<CandidateCourse> filter(List<CandidateCourse> candidates, OptimizationConstraints constraints) {
        return candidates.stream()
                .filter(course -> course.timeSlots().stream().noneMatch(slot -> isExcludedDay(slot, constraints)))
                .filter(course -> course.timeSlots().stream().allMatch(slot -> isWithinAvailableTime(slot, constraints)))
                .toList();
    }

    private boolean isExcludedDay(CourseTimeSlot slot, OptimizationConstraints constraints) {
        return constraints.excludedDays().contains(slot.dayOfWeek());
    }

    private boolean isWithinAvailableTime(CourseTimeSlot slot, OptimizationConstraints constraints) {
        return !slot.startTime().isBefore(constraints.availableTimeStart())
                && !slot.endTime().isAfter(constraints.availableTimeEnd());
    }
}
