package com.example.pl_timetable_project.completedcourse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
class CompletedCourseOcrMatchRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private CompletedCourseOcrMatchRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                INSERT INTO semesters (
                    id, prepared_at, dataset_version, source_checksum,
                    is_active, created_at
                ) VALUES (
                    '2026-2', DATE '2026-08-01', 'ocr-match-test-v1',
                    repeat('c', 64), true, now()
                );

                INSERT INTO courses (
                    semester_id, course_code, name, category, credits
                ) VALUES (
                    '2026-2', '927381',
                    'LCT(LearningbyCommunication&Teamwork)',
                    '교양필수', 2.00
                );

                INSERT INTO sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes
                ) VALUES (
                    '2026-2', '927381', '08', '김승남',
                    '목11:30-13:30', false, '[]'::jsonb
                );

                INSERT INTO rooms (
                    semester_id, code, building_name, label
                ) VALUES (
                    '2026-2', 'R110', '인문학관', '인110-일반강의실'
                );

                INSERT INTO sessions (
                    semester_id, course_code, section_code, day,
                    start_minute, end_minute, room_code, sequence_no
                ) VALUES (
                    '2026-2', '927381', '08', '목',
                    690, 810, 'R110', 1
                );
                """);
    }

    @Test
    void findsPunctuationAndWhitespaceVariantAndLoadsSessionRoom() {
        var candidates = repository.findCandidates(
                Set.of("2026-2"),
                Set.of("lctlearningbycommunicationteamwork"));

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.semesterId()).isEqualTo("2026-2");
            assertThat(candidate.courseCode()).isEqualTo("927381");
            assertThat(candidate.sectionCode()).isEqualTo("08");
            assertThat(candidate.professor()).isEqualTo("김승남");
            assertThat(candidate.sessions()).singleElement().satisfies(session -> {
                assertThat(session.dayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
                assertThat(session.startTime()).isEqualTo(LocalTime.of(11, 30));
                assertThat(session.endTime()).isEqualTo(LocalTime.of(13, 30));
                assertThat(session.roomLabel()).isEqualTo("인110-일반강의실");
            });
        });
    }
}
