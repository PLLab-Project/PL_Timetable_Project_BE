package com.example.pl_timetable_project.academic.course;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * course_sequence_hints(수동 큐레이션된 선수과목→후속과목 코드 쌍)를 읽는다.
 * 공식 선수과목 시스템이 아니라 자동편성 가중치 힌트로만 쓰는 최소한의 데이터다.
 */
@Repository
public class CourseSequenceHintRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CourseSequenceHintRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * followUpCourseCodes 중 course_sequence_hints에 등록된 것만 골라
     * 후속과목 코드 → 선수과목 코드 목록으로 묶어 돌려준다. 힌트가 없는
     * 과목 코드는 결과 맵에 아예 나타나지 않는다.
     */
    public Map<String, List<String>> findPrerequisitesByFollowUpCourseCodes(
            Collection<String> followUpCourseCodes) {
        if (followUpCourseCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> result = new HashMap<>();
        jdbcTemplate.query("""
                SELECT prerequisite_course_code, follow_up_course_code
                  FROM course_sequence_hints
                 WHERE follow_up_course_code IN (:followUpCourseCodes)
                """, Map.of("followUpCourseCodes", followUpCourseCodes), rs -> {
            result.computeIfAbsent(
                            rs.getString("follow_up_course_code"), key -> new ArrayList<>())
                    .add(rs.getString("prerequisite_course_code"));
        });
        return Map.copyOf(result);
    }
}
