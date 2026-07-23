package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.academic.section.SectionReference;
import com.example.pl_timetable_project.exception.RequiredCourseConflictException;
import com.example.pl_timetable_project.exception.RequiredCourseExcludedByConditionException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 사용자가 필수로 지정한 분반을 먼저 배치하고 나머지 후보를 분리한다.
 */
@Component
public class RequiredCoursePlacer {

    public RequiredPlacementResult place(
            List<CandidateCourse> filteredCandidates,
            Set<SectionReference> requiredSections) {
        List<CandidateCourse> required = filteredCandidates.stream()
                .filter(course -> requiredSections.contains(course.section()))
                .toList();

        validateAllRequiredSectionsPresent(required, requiredSections);
        validateNoConflictAmongRequired(required);

        List<CandidateCourse> optional = filteredCandidates.stream()
                .filter(course -> !requiredSections.contains(course.section()))
                .filter(course -> required.stream().noneMatch(course::conflictsWith))
                .toList();

        return new RequiredPlacementResult(required, optional);
    }

    private void validateAllRequiredSectionsPresent(
            List<CandidateCourse> required,
            Set<SectionReference> requiredSections) {
        Set<SectionReference> found = required.stream()
                .map(CandidateCourse::section)
                .collect(Collectors.toSet());
        Set<String> missing = requiredSections.stream()
                .filter(section -> !found.contains(section))
                .map(SectionReference::displayKey)
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new RequiredCourseExcludedByConditionException(
                    "필수 분반이 후보에 없거나 시간 조건에서 제외됐습니다. sections=" + missing);
        }
    }

    private void validateNoConflictAmongRequired(List<CandidateCourse> required) {
        for (int i = 0; i < required.size(); i++) {
            for (int j = i + 1; j < required.size(); j++) {
                CandidateCourse left = required.get(i);
                CandidateCourse right = required.get(j);
                if (left.conflictsWith(right)) {
                    throw new RequiredCourseConflictException(
                            "필수 분반끼리 중복되거나 시간이 겹칩니다: "
                                    + left.section().displayKey() + " vs "
                                    + right.section().displayKey());
                }
            }
        }
    }
}
