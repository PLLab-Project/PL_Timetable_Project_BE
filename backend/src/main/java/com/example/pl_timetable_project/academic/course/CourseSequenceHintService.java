package com.example.pl_timetable_project.academic.course;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * course_sequence_hints 조회 결과를 자동편성이 바로 쓸 수 있는 형태로 다듬는다.
 * 이미 이수한 과목 코드 집합(completedCourseCodes)과 그대로 비교할 수 있도록
 * 선수과목 코드를 소문자로 정규화해서 돌려준다.
 */
@Component
public class CourseSequenceHintService {

    private final CourseSequenceHintRepository repository;

    public CourseSequenceHintService(CourseSequenceHintRepository repository) {
        this.repository = repository;
    }

    /**
     * followUpCourseCodes에 있는 과목 코드마다 등록된 선수과목 코드 목록(소문자
     * 정규화)을 돌려준다. 힌트가 없는 과목도 결과 맵에 빈 리스트로 포함된다.
     */
    public Map<String, List<String>> findPrerequisites(
            Collection<String> followUpCourseCodes) {
        Map<String, List<String>> hints =
                repository.findPrerequisitesByFollowUpCourseCodes(followUpCourseCodes);
        Map<String, List<String>> result = new HashMap<>();
        for (String followUpCourseCode : followUpCourseCodes) {
            List<String> prerequisites = hints.getOrDefault(followUpCourseCode, List.of())
                    .stream()
                    .map(code -> code.toLowerCase(Locale.ROOT))
                    .toList();
            result.put(followUpCourseCode, prerequisites);
        }
        return Map.copyOf(result);
    }
}
