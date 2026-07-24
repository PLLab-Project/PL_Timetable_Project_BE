package com.example.pl_timetable_project;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

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

        assertThat(version).startsWith("18.4");
assertThat(successfulMigrations).isEqualTo(8);
        assertThat(graduationProfiles).isEqualTo("graduation_credit_profiles");
        assertThat(socialIdentities).isEqualTo("social_identities");
        assertThat(academicUnits).isEqualTo("academic_units");
        assertThat(sectionAcademicUnits).isEqualTo("section_academic_units");
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

    private void executeAcademicUnitNormalization() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(
                connection,
                new ClassPathResource("db/normalization/normalize_academic_units.sql"));
    }

}
