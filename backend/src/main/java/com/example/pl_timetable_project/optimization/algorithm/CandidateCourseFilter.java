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
                .filter(course -> !course.timeSlots().isEmpty())
                .filter(course -> course.timeSlots().stream().noneMatch(slot -> isExcludedDay(slot, constraints)))
                .filter(course -> course.timeSlots().stream().allMatch(slot -> isWithinAvailableTime(slot, constraints)))
                .filter(course -> course.timeSlots().stream().noneMatch(slot ->
                        constraints.blockedTimes().stream().anyMatch(slot::overlaps)))
                .filter(course -> matchesUserAcademicUnit(course, constraints))
                .toList();
    }

    private boolean isExcludedDay(CourseTimeSlot slot, OptimizationConstraints constraints) {
        return constraints.excludedDays().contains(slot.dayOfWeek());
    }

    private boolean isWithinAvailableTime(CourseTimeSlot slot, OptimizationConstraints constraints) {
        return constraints.availableTimes().stream().anyMatch(range -> range.contains(slot));
    }

    /**
     * 강의에 학과 제한이 없거나(교양·미분류), 제한된 학과 중 하나라도 사용자의
     * 학과 코드 목록에 있으면 후보로 남긴다. 사용자의 학과 코드 목록이 비어 있으면
     * (예: 프로필 미완성) 학과 필터를 적용하지 않는다.
     */
    private boolean matchesUserAcademicUnit(CandidateCourse course, OptimizationConstraints constraints) {
        if (constraints.userAcademicUnitCodes().isEmpty()) {
            return true;
        }
        if (course.restrictedAcademicUnitCodes().isEmpty()) {
            return true;
        }
        return course.restrictedAcademicUnitCodes().stream()
                .anyMatch(constraints.userAcademicUnitCodes()::contains);
    }
}
