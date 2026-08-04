package com.example.pl_timetable_project.optimization.algorithm;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 제외 요일에 해당하거나 수업 가능 시간대를 벗어나는 슬롯을 가진 강의를 후보에서 제거한다.
 * 강의의 슬롯 중 하나라도 조건을 위반하면 그 강의 전체를 후보에서 제외한다. 비고(notes)
 * 문구로 확인된 "진짜 학과 제한" 강의도 여기서 완전히 배제한다(하드 제외 — 학과
 * 가중치(SAME_MAJOR_BONUS)와 달리 소프트 처리 대상이 아니다).
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
                .filter(course -> !isHardRestrictedToOtherAcademicUnit(course, constraints))
                .toList();
    }

    private boolean isExcludedDay(CourseTimeSlot slot, OptimizationConstraints constraints) {
        return constraints.excludedDays().contains(slot.dayOfWeek());
    }

    private boolean isWithinAvailableTime(CourseTimeSlot slot, OptimizationConstraints constraints) {
        return constraints.availableTimes().stream().anyMatch(range -> range.contains(slot));
    }

    /**
     * 강의의 hardRestrictedAcademicUnitCode가 채워져 있고(=notes로 확인된 진짜
     * 학과 제한) 사용자의 학과 코드 목록에 그 학과가 없으면 배제한다. 제한 학과가
     * 없거나(null) 사용자 학과 정보 자체가 없으면(프로필 미완성) 안전하게 배제하지
     * 않는다 — 판단 근거가 불확실할 땐 후보에서 빼지 않는 쪽을 기본값으로 한다.
     */
    private boolean isHardRestrictedToOtherAcademicUnit(
            CandidateCourse course, OptimizationConstraints constraints) {
        String restrictedAcademicUnitCode = course.hardRestrictedAcademicUnitCode();
        if (restrictedAcademicUnitCode == null || constraints.userAcademicUnitCodes().isEmpty()) {
            return false;
        }
        return !constraints.userAcademicUnitCodes().contains(restrictedAcademicUnitCode);
    }
}
