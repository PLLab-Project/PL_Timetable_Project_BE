package com.example.pl_timetable_project.completedcourse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
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
class CompletedCourseOfferingQueryRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private CompletedCourseOfferingQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                INSERT INTO academic_colleges (
                    code, name, first_seen_year, last_seen_year, is_current
                ) VALUES (
                    'OCR-COLLEGE', 'OCR 테스트 단과대학', 2020, 2026, true
                );

                INSERT INTO academic_units (
                    code, college_code, name, code_source,
                    first_seen_year, last_seen_year, is_current
                ) VALUES (
                    'OCR-MAJOR', 'OCR-COLLEGE', 'OCR 테스트 전공',
                    'OFFICIAL_CURRICULUM', 2020, 2026, true
                ), (
                    'OCR-OTHER', 'OCR-COLLEGE', '다른 전공',
                    'OFFICIAL_CURRICULUM', 2020, 2026, true
                );

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

                INSERT INTO catalog_sources (
                    checksum, semester_id, source_kind, original_file_name,
                    published_on, parser_version, raw_row_count,
                    unique_section_count, metadata
                ) VALUES (
                    repeat('d', 64), '2026-2', 'OFFICIAL_PDF',
                    'ocr-fixture.pdf', current_date, 'ocr-test-parser',
                    1, 1, '{}'::jsonb
                );

                INSERT INTO section_classification_contexts (
                    semester_id, course_code, section_code, source_checksum,
                    context_label, context_kind, academic_unit_code,
                    completion_category, target_grade, is_primary, is_shaded,
                    source_page, source_row
                ) VALUES (
                    '2026-2', '927381', '08', repeat('d', 64),
                    'OCR 테스트 전공', 'ACADEMIC_UNIT', 'OCR-MAJOR',
                    '전선', '2학년', true, false, 1, 1
                ), (
                    '2026-2', '927381', '08', repeat('d', 64),
                    '다른 전공', 'ACADEMIC_UNIT', 'OCR-OTHER',
                    '교필', '2학년', true, false, 1, 2
                );

                INSERT INTO historical_term_datasets (
                    id, academic_year, term_code, term_name, data_status,
                    schema_version, collected_at, source_checksum, record_count,
                    raw_payload, source_archive, imported_at
                ) VALUES (
                    'ocr-term-2020-1', 2020, '1', '1학기', 'COMPLETE', '1',
                    now(), repeat('e', 64), 1, '{}'::json, decode('', 'hex'), now()
                ), (
                    'ocr-term-2026-2', 2026, '2', '2학기', 'COMPLETE', '1',
                    now(), repeat('f', 64), 1, '{}'::json, decode('', 'hex'), now()
                );

                INSERT INTO historical_course_offerings (
                    id, dataset_id, academic_year, term_code, course_code,
                    section_code, korean_name, english_name, professor_name,
                    completion_category, credits, lecture_hours, practice_hours,
                    raw_lecture_time, raw_location, target_grade, listing_status,
                    detail_status, category_contexts, department_contexts,
                    search_text, department_search_text, raw_payload
                ) VALUES (
                    'history-2020-1-927313-01', 'ocr-term-2020-1',
                    2020, '1', '927313', '01', '컴퓨팅사고와문제해결', NULL,
                    '김지연', '교필', 2, 2, 0, '수11:30-13:30',
                    '정보 310 스마트강의실', NULL, 'LISTED', 'AVAILABLE',
                    '[]'::json, '[]'::json, '컴퓨팅사고와문제해결', '', '{}'::json
                ), (
                    'history-2026-2-927381-08', 'ocr-term-2026-2',
                    2026, '2', '927381', '08',
                    'LCT(LearningbyCommunication&Teamwork)', NULL,
                    '김승남', '교필', 2, 2, 0, '목11:30-13:30',
                    '인110-일반강의실', NULL, 'LISTED', 'AVAILABLE',
                    '[]'::json, '[]'::json,
                    'LCT LearningbyCommunication Teamwork', '', '{}'::json
                );
                """);
        jdbcTemplate.execute("SELECT reconcile_historical_course_offerings()");
    }

    @Test
    void findsPunctuationAndWhitespaceVariantAndLoadsSessionRoom() {
        var candidates = repository.findCandidates(
                Set.of("2026-2"),
                Set.of("lctlearningbycommunicationteamwork"),
                List.of("OCR-MAJOR"));

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.semesterId()).isEqualTo("2026-2");
            assertThat(candidate.courseCode()).isEqualTo("927381");
            assertThat(candidate.sectionCode()).isEqualTo("08");
            assertThat(candidate.historicalOfferingId())
                    .isEqualTo("history-2026-2-927381-08");
            assertThat(candidate.professor()).isEqualTo("김승남");
            assertThat(candidate.credits()).isEqualByComparingTo("2.0");
            assertThat(candidate.completionCategory()).isEqualTo("전선");
            assertThat(candidate.sessions()).singleElement().satisfies(session -> {
                assertThat(session.dayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
                assertThat(session.startTime()).isEqualTo(LocalTime.of(11, 30));
                assertThat(session.endTime()).isEqualTo(LocalTime.of(13, 30));
                assertThat(session.roomLabel()).isEqualTo("인110-일반강의실");
            });
        });
    }

    @Test
    void findsHistoricalOfferingAndParsesItsRawMeeting() {
        var candidates = repository.findCandidates(
                Set.of("2020-1"),
                Set.of("컴퓨팅사고와문제해결"));

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.historicalOfferingId())
                    .isEqualTo("history-2020-1-927313-01");
            assertThat(candidate.semesterId()).isEqualTo("2020-1");
            assertThat(candidate.courseCode()).isEqualTo("927313");
            assertThat(candidate.sectionCode()).isEqualTo("01");
            assertThat(candidate.credits()).isEqualByComparingTo("2.0");
            assertThat(candidate.completionCategory()).isEqualTo("교필");
            assertThat(candidate.sessions()).singleElement().satisfies(session -> {
                assertThat(session.dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
                assertThat(session.startTime()).isEqualTo(LocalTime.of(11, 30));
                assertThat(session.endTime()).isEqualTo(LocalTime.of(13, 30));
                assertThat(session.roomLabel()).isEqualTo("정보 310 스마트강의실");
            });
        });
    }

    @Test
    void exposesHistoricalSemesterIds() {
        assertThat(repository.findHistoricalSemesterIds())
                .contains("2020-1", "2026-2");
    }

    @Test
    void doesNotBorrowAnotherDepartmentsCompletionCategoryWithoutPreference() {
        var candidates = repository.findCandidates(
                Set.of("2026-2"),
                Set.of("lctlearningbycommunicationteamwork"));

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.category()).isEqualTo("교양필수");
            assertThat(candidate.completionCategory()).isNull();
        });
    }
}
