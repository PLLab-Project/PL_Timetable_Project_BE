package com.example.pl_timetable_project.academic.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * course_sequence_hints에 실제로 적재된 마이그레이션 초기 데이터(기초미적분학 →
 * 응용미적분학/공업수학II)로 조회가 동작하는지 검증한다.
 */
@SpringBootTest
@Testcontainers
@Transactional
class CourseSequenceHintRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private CourseSequenceHintRepository repository;

    @Test
    void findsCuratedPrerequisitesForCourseCode005112AndUnrelatedCourse() {
        Map<String, List<String>> hints = repository.findPrerequisitesByFollowUpCourseCodes(
                List.of("005112", "924011", "999999"));

        assertThat(hints.get("005112")).containsExactly("005111");
        assertThat(hints.get("924011")).containsExactly("005111");
        assertThat(hints).doesNotContainKey("999999");
    }

    @Test
    void returnsEmptyMapForEmptyInput() {
        Map<String, List<String>> hints =
                repository.findPrerequisitesByFollowUpCourseCodes(List.of());

        assertThat(hints).isEmpty();
    }
}
