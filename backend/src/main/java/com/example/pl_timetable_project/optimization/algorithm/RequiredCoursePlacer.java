package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.exception.RequiredCourseConflictException;
import com.example.pl_timetable_project.exception.RequiredCourseExcludedByConditionException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 필수 강의를 먼저 확정하고, 필수 강의끼리 충돌하면 즉시 실패시킨다.
 * 필수 강의와 이미 충돌하는 선택 강의는 백트래킹 탐색을 줄이기 위해 미리 후보에서 제거한다.
 */
@Component
public class RequiredCoursePlacer {

    public RequiredPlacementResult place(List<CandidateCourse> filteredCandidates, Set<Long> requiredCourseIds) {
        List<CandidateCourse> required = filteredCandidates.stream()
                .filter(course -> requiredCourseIds.contains(course.courseId()))
                .toList();

        validateAllRequiredCoursesPresent(required, requiredCourseIds);
        validateNoConflictAmongRequired(required);

        List<CandidateCourse> optional = filteredCandidates.stream()
                .filter(course -> !requiredCourseIds.contains(course.courseId()))
                .filter(course -> required.stream().noneMatch(course::conflictsWith))
                .toList();

        return new RequiredPlacementResult(required, optional);
    }

    private void validateAllRequiredCoursesPresent(List<CandidateCourse> required, Set<Long> requiredCourseIds) {
        Set<Long> foundIds = required.stream().map(CandidateCourse::courseId).collect(Collectors.toSet());
        Set<Long> missing = requiredCourseIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new RequiredCourseExcludedByConditionException(
                    "필수 강의 중 후보 목록에 없거나 조건(제외 요일/수업 가능 시간대)에 맞지 않는 강의가 있습니다. courseIds=" + missing);
        }
    }

    private void validateNoConflictAmongRequired(List<CandidateCourse> required) {
        for (int i = 0; i < required.size(); i++) {
            for (int j = i + 1; j < required.size(); j++) {
                CandidateCourse a = required.get(i);
                CandidateCourse b = required.get(j);
                if (a.conflictsWith(b)) {
                    throw new RequiredCourseConflictException(
                            "필수 강의끼리 시간이 겹칩니다: '" + a.courseName() + "' vs '" + b.courseName() + "'");
                }
            }
        }
    }
}
