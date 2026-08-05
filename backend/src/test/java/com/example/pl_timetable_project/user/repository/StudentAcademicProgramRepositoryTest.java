package com.example.pl_timetable_project.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
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

/**
 * findMajorAcademicUnitCodes()가 자동편성·OCR 매칭에서 "본인 학과"로 취급할 학과
 * 코드를 정확히 골라내는지 검증한다 — PRIMARY·DOUBLE_MAJOR는 포함하고,
 * MINOR·MICRO_MAJOR·WITHDRAWN은 제외한다.
 */
@SpringBootTest
@Testcontainers
@Transactional
class StudentAcademicProgramRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StudentAcademicProgramRepository repository;

    private UUID userId;

    @BeforeEach
    void setUpStudent() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("insert into users (id) values (?)", userId);
        jdbcTemplate.update("""
                insert into academic_units (code, name, code_source, first_seen_year, last_seen_year, is_current)
                values
                    ('D1', '컴퓨터공학과', 'OFFICIAL_CURRICULUM', 2020, 2099, true),
                    ('D2', '경영학과', 'OFFICIAL_CURRICULUM', 2020, 2099, true),
                    ('D3', '심리학과', 'OFFICIAL_CURRICULUM', 2020, 2099, true),
                    ('D4', '체육학과', 'OFFICIAL_CURRICULUM', 2020, 2099, true)
                """);
    }

    @Test
    void includesPrimaryAndDoubleMajorButExcludesMinorMicroMajorAndWithdrawn() {
        jdbcTemplate.update("""
                insert into student_academic_programs (
                    user_id, academic_unit_code, program_role, status, display_order)
                values
                    (?, 'D1', 'PRIMARY', 'ACTIVE', 0),
                    (?, 'D2', 'DOUBLE_MAJOR', 'ACTIVE', 1),
                    (?, 'D3', 'MINOR', 'ACTIVE', 2),
                    (?, 'D4', 'MICRO_MAJOR', 'ACTIVE', 3)
                """, userId, userId, userId, userId);

        List<String> codes = repository.findMajorAcademicUnitCodes(userId);

        assertThat(codes).containsExactlyInAnyOrder("D1", "D2");
    }

    @Test
    void excludesWithdrawnDoubleMajor() {
        jdbcTemplate.update("""
                insert into student_academic_programs (
                    user_id, academic_unit_code, program_role, status, display_order)
                values
                    (?, 'D1', 'PRIMARY', 'ACTIVE', 0),
                    (?, 'D2', 'DOUBLE_MAJOR', 'WITHDRAWN', 1)
                """, userId, userId);

        List<String> codes = repository.findMajorAcademicUnitCodes(userId);

        assertThat(codes).containsExactly("D1");
    }

    @Test
    void returnsEmptyListWhenStudentHasNoDeclaredPrograms() {
        List<String> codes = repository.findMajorAcademicUnitCodes(userId);

        assertThat(codes).isEmpty();
    }
}
