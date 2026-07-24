package com.example.pl_timetable_project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pl_timetable_project.exception.SectionConflictException;
import com.example.pl_timetable_project.optimization.dto.request.CourseCandidateRequest;
import com.example.pl_timetable_project.optimization.dto.request.OptimizationCreateRequest;
import com.example.pl_timetable_project.optimization.dto.request.TimeRangeRequest;
import com.example.pl_timetable_project.optimization.dto.response.OptimizationJobResponse;
import com.example.pl_timetable_project.optimization.entity.OptimizationJobStatus;
import com.example.pl_timetable_project.optimization.service.OptimizationService;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCourseRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCreateRequest;
import com.example.pl_timetable_project.timetable.dto.response.TimetableResponse;
import com.example.pl_timetable_project.timetable.service.TimetableService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class PlTimetableProjectApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TimetableService timetableService;

    @Autowired
    OptimizationService optimizationService;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayCreatesTheSharedDatabaseContract() {
        String version = jdbcTemplate.queryForObject("SHOW server_version", String.class);
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success IS TRUE",
                Integer.class);
        String graduationProfiles = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.graduation_credit_profiles')::text",
                String.class);
        String socialIdentities = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.social_identities')::text",
                String.class);
        String academicUnits = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.academic_units')::text",
                String.class);
        String sectionAcademicUnits = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.section_academic_units')::text",
                String.class);
        String timetables = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.timetables')::text",
                String.class);
        String optimizationJobs = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.optimization_jobs')::text",
                String.class);

        assertThat(version).startsWith("18.4");
        assertThat(successfulMigrations).isEqualTo(8);
        assertThat(graduationProfiles).isEqualTo("graduation_credit_profiles");
        assertThat(socialIdentities).isEqualTo("social_identities");
        assertThat(academicUnits).isEqualTo("academic_units");
        assertThat(sectionAcademicUnits).isEqualTo("section_academic_units");
        assertThat(timetables).isEqualTo("timetables");
        assertThat(optimizationJobs).isEqualTo("optimization_jobs");
    }

    @Test
    void normalizesStudentProfileAcademicUnitsAndAddsForeignKeySupportIndexes() {
        List<String> profileColumns = jdbcTemplate.queryForList("""
                SELECT column_name
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = 'student_profiles'
                 ORDER BY ordinal_position
                """, String.class);
        Integer academicUnitForeignKeys = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM information_schema.table_constraints constraint_info
                  JOIN information_schema.key_column_usage column_info
                    ON column_info.constraint_schema = constraint_info.constraint_schema
                   AND column_info.constraint_name = constraint_info.constraint_name
                 WHERE constraint_info.table_schema = 'public'
                   AND constraint_info.table_name = 'student_profiles'
                   AND constraint_info.constraint_type = 'FOREIGN KEY'
                   AND column_info.column_name = 'academic_unit_code'
                """, Integer.class);

        assertThat(profileColumns)
                .contains("academic_unit_code")
                .doesNotContain("academic_unit_name", "academic_unit_key");
        assertThat(academicUnitForeignKeys).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.ix_student_profiles_requirement_lookup')::text",
                String.class)).isEqualTo("ix_student_profiles_requirement_lookup");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.ix_sessions_room')::text",
                String.class)).isEqualTo("ix_sessions_room");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.ix_optimization_required_sections_section')::text",
                String.class)).isEqualTo("ix_optimization_required_sections_section");
    }

    @Test
    @Transactional
    void normalizesAcademicUnitsAndSectionMappingsIdempotently() {
        jdbcTemplate.execute("""
                INSERT INTO historical_curriculum_datasets (
                    id, academic_year, schema_version, collected_at, source_checksum,
                    department_count, course_record_count, raw_payload, source_archive, imported_at
                ) VALUES (
                    'test-curriculum-2026', 2026, '1', now(), repeat('a', 64),
                    1, 1, '{}'::json, decode('', 'hex'), now()
                );

                INSERT INTO historical_curriculum_departments (
                    id, dataset_id, academic_year, college_code, college_name,
                    department_code, department_name, course_count, raw_payload
                ) VALUES (
                    'test-department', 'test-curriculum-2026', 2026, 'C1', '공과대학',
                    'D1', '컴퓨터공학과', 1, '{}'::json
                );

                INSERT INTO requirement_datasets (
                    id, kind, schema_version, admission_year, effective_year, as_of,
                    source_path, source_checksum, normalized_checksum, record_count,
                    raw_payload, imported_at
                ) VALUES (
                    'test-requirements', 'CURRICULUM_REQUIRED', '1', 2018, NULL, current_date,
                    'test/requirements', repeat('d', 64), repeat('e', 64), 1,
                    '{}'::json, now()
                );

                INSERT INTO curriculum_program_requirements (
                    id, dataset_id, admission_year, academic_unit, academic_unit_key,
                    status, source_locators, source_course_count, required_course_count, raw_payload
                ) VALUES (
                    'test-program', 'test-requirements', 2018, '미래융합전공', '미래융합전공',
                    'NORMALIZED', '[]'::json, 0, 0, '{}'::json
                );

                INSERT INTO historical_term_datasets (
                    id, academic_year, term_code, term_name, data_status, schema_version,
                    collected_at, source_checksum, record_count, raw_payload, source_archive, imported_at
                ) VALUES (
                    'test-term-2026-1', 2026, '1', '1학기', 'COMPLETE', '1',
                    now(), repeat('b', 64), 1, '{}'::json, decode('', 'hex'), now()
                );

                INSERT INTO historical_course_offerings (
                    id, dataset_id, academic_year, term_code, course_code, section_code,
                    korean_name, english_name, professor_name, completion_category,
                    credits, lecture_hours, practice_hours, raw_lecture_time, raw_location,
                    target_grade, listing_status, detail_status, category_contexts,
                    department_contexts, search_text, department_search_text, raw_payload
                ) VALUES (
                    'test-offering', 'test-term-2026-1', 2026, '1', 'CSE100', '01',
                    '자료구조', NULL, NULL, '전공', 3, 3, 0, '월1-3', '공학관',
                    '2', 'LISTED', 'AVAILABLE', '[]'::json,
                    '[{"collegeCode":"C1","collegeName":"공과대학","departmentCode":"D1","departmentName":"컴퓨터공학과"}]'::json,
                    '자료구조', '컴퓨터공학과', '{}'::json
                );

                INSERT INTO semesters (
                    id, prepared_at, dataset_version, source_checksum, is_active, created_at
                ) VALUES (
                    '2026-1', current_date, 'test-dataset-v1', repeat('c', 64), true, now()
                );

                INSERT INTO courses (semester_id, course_code, name, category, credits)
                VALUES ('2026-1', 'CSE100', '자료구조', '전공', 3);

                INSERT INTO sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes
                ) VALUES (
                    '2026-1', 'CSE100', '01', NULL, '월1-3', false, '[]'::jsonb
                );
                """);

        executeAcademicUnitNormalization();
        executeAcademicUnitNormalization();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM academic_colleges", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM academic_units", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT normalized_key FROM academic_units WHERE code = 'D1'",
                String.class)).isEqualTo("컴퓨터공학과");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM academic_unit_aliases WHERE academic_unit_code = 'D1'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM academic_units WHERE code_source = 'REQUIREMENT_DERIVED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT academic_unit_code FROM curriculum_program_requirements WHERE id = 'test-program'",
                String.class)).startsWith("REQ-");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM section_academic_units WHERE academic_unit_code = 'D1'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @Transactional
    void persistsTimetableWithCanonicalAcademicSectionAndUuidOwner() {
        UUID userId = insertTimetableFixture();

        TimetableResponse response = timetableService.createTimetable(
                userId,
                new TimetableCreateRequest(
                        "2026-1 기본 시간표",
                        "2026-1",
                        List.of(new TimetableCourseRequest("CSE100", "01"))));
        entityManager.flush();

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.semesterId()).isEqualTo("2026-1");
        assertThat(response.totalCredits()).isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(response.sections()).hasSize(1);
        assertThat(response.sections().get(0).courseName()).isEqualTo("자료구조");
        assertThat(response.sections().get(0).professorName()).isEqualTo("홍길동");
        assertThat(response.sections().get(0).meetings()).hasSize(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM timetable_courses WHERE timetable_id = ?",
                Integer.class,
                response.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM timetable_course_meetings m
                  JOIN timetable_courses c ON c.id = m.timetable_course_id
                 WHERE c.timetable_id = ?
                """,
                Integer.class,
                response.id())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT start_minute, end_minute
                  FROM timetable_course_meetings m
                  JOIN timetable_courses c ON c.id = m.timetable_course_id
                 WHERE c.timetable_id = ?
                 ORDER BY position
                """,
                response.id()))
                .extracting(row -> List.of(
                        ((Number) row.get("start_minute")).intValue(),
                        ((Number) row.get("end_minute")).intValue()))
                .containsExactlyInAnyOrder(
                        List.of(540, 630),
                        List.of(780, 830));

        entityManager.clear();
        TimetableResponse reloaded = timetableService.getTimetable(userId, response.id());
        assertThat(reloaded.sections().get(0).meetings())
                .extracting(meeting -> List.of(meeting.startTime(), meeting.endTime()))
                .containsExactlyInAnyOrder(
                        List.of(LocalTime.of(9, 0), LocalTime.of(10, 30)),
                        List.of(LocalTime.of(13, 0), LocalTime.of(13, 50)));
    }

    @Test
    @Transactional
    void rejectsTwoSectionsOfTheSameCourse() {
        UUID userId = insertTimetableFixture();
        jdbcTemplate.execute("""
                INSERT INTO sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes
                ) VALUES (
                    '2026-1', 'CSE100', '02', '김교수',
                    '수1-3', false, '[]'::jsonb
                );

                INSERT INTO sessions (
                    semester_id, course_code, section_code, day, start_minute, end_minute
                ) VALUES (
                    '2026-1', 'CSE100', '02', '수', 540, 690
                );
                """);

        TimetableCreateRequest request = new TimetableCreateRequest(
                "중복 분반 시간표",
                "2026-1",
                List.of(
                        new TimetableCourseRequest("CSE100", "01"),
                        new TimetableCourseRequest("CSE100", "02")));

        assertThatThrownBy(() -> timetableService.createTimetable(userId, request))
                .isInstanceOf(SectionConflictException.class)
                .hasMessageContaining("같은 과목");
    }

    @Test
    @Transactional
    void persistsOptimizationJobWithCanonicalRequiredSection() {
        UUID userId = insertTimetableFixture();
        TimetableResponse timetable = timetableService.createTimetable(
                userId,
                new TimetableCreateRequest("자동 편성", "2026-1", List.of()));

        OptimizationJobResponse job = optimizationService.createJob(
                userId,
                new OptimizationCreateRequest(
                        timetable.id(),
                        new BigDecimal("3.00"),
                        new BigDecimal("3.00"),
                        new BigDecimal("3.00"),
                        java.util.Set.of(),
                        new TimeRangeRequest(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        new TimeRangeRequest(LocalTime.of(12, 0), LocalTime.of(13, 0)),
                        480,
                        List.of(new CourseCandidateRequest("CSE100", "01", true))));
        entityManager.flush();

        assertThat(job.userId()).isEqualTo(userId);
        assertThat(job.semesterId()).isEqualTo("2026-1");
        assertThat(job.status()).isEqualTo(OptimizationJobStatus.PENDING);
        var storedTimes = jdbcTemplate.queryForMap(
                """
                SELECT available_start_minute, available_end_minute,
                       lunch_start_minute, lunch_end_minute
                  FROM optimization_jobs
                 WHERE id = ?
                """,
                job.id());
        assertThat(((Number) storedTimes.get("available_start_minute")).intValue()).isEqualTo(480);
        assertThat(((Number) storedTimes.get("available_end_minute")).intValue()).isEqualTo(1200);
        assertThat(((Number) storedTimes.get("lunch_start_minute")).intValue()).isEqualTo(720);
        assertThat(((Number) storedTimes.get("lunch_end_minute")).intValue()).isEqualTo(780);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                  FROM optimization_job_required_sections
                 WHERE job_id = ?
                   AND semester_id = '2026-1'
                   AND course_code = 'CSE100'
                   AND section_code = '01'
                """,
                Integer.class,
                job.id())).isEqualTo(1);
    }

    private UUID insertTimetableFixture() {
        UUID userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users (display_name, primary_email)
                VALUES ('테스트 사용자', 'timetable@example.com')
                RETURNING id
                """,
                UUID.class);
        jdbcTemplate.execute("""
                INSERT INTO semesters (
                    id, prepared_at, dataset_version, source_checksum, is_active, created_at
                ) VALUES (
                    '2026-1', current_date, 'test-timetable-v1', repeat('f', 64), true, now()
                );

                INSERT INTO courses (semester_id, course_code, name, category, credits)
                VALUES ('2026-1', 'CSE100', '자료구조', '전공', 3.00);

                INSERT INTO sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes
                ) VALUES (
                    '2026-1', 'CSE100', '01', '홍길동',
                    '월1-2,금3', false, '[]'::jsonb
                );

                INSERT INTO sessions (
                    semester_id, course_code, section_code, day, start_minute, end_minute
                ) VALUES
                    ('2026-1', 'CSE100', '01', '월', 540, 630),
                    ('2026-1', 'CSE100', '01', '금', 780, 830);
                """);
        return userId;
    }

    private void executeAcademicUnitNormalization() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(
                connection,
                new ClassPathResource("db/normalization/normalize_academic_units.sql"));
    }

}
