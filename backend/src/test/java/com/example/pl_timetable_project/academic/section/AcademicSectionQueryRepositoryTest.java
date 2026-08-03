package com.example.pl_timetable_project.academic.section;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
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
 * 학과 필터가 section_academic_units 구조화된 데이터를 기준으로
 * "학과 전용 분반 제외 / 공통·교양·미분류 분반 포함"을 지키는지 검증한다.
 */
@SpringBootTest
@Testcontainers
@Transactional
class AcademicSectionQueryRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AcademicSectionQueryRepository sectionQueryRepository;

    @BeforeEach
    void setUpCatalog() {
        jdbcTemplate.update("""
                insert into semesters
                    (id, prepared_at, dataset_version, source_checksum, is_active, created_at)
                values ('2099-1', current_date, 'test', 'test', true, now())
                """);

        jdbcTemplate.update("""
                insert into academic_units (code, name, code_source, first_seen_year, last_seen_year, is_current)
                values
                    ('D1', '컴퓨터공학과', 'OFFICIAL_CURRICULUM', 2020, 2099, true),
                    ('D2', '교양학부', 'OFFICIAL_CURRICULUM', 2020, 2099, true)
                """);

        jdbcTemplate.update("""
                insert into courses (semester_id, course_code, name, category, credits)
                values
                    ('2099-1', 'CSE100', 'D1 전공필수', '전공필수', 3),
                    ('2099-1', 'PHY100', 'D2 전공필수', '전공필수', 3),
                    ('2099-1', 'GEN100', '교양 과목', '교양선택', 2),
                    ('2099-1', 'UNTAGGED100', '미분류 전공선택', '전공선택', 3)
                """);

        jdbcTemplate.update("""
                insert into sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes)
                values
                    ('2099-1', 'CSE100', '01', '김교수', '', false, '[]'),
                    ('2099-1', 'PHY100', '01', '이교수', '', false, '[]'),
                    ('2099-1', 'GEN100', '01', '박교수', '', false, '[]'),
                    ('2099-1', 'UNTAGGED100', '01', '최교수', '', false, '[]')
                """);

        jdbcTemplate.update("""
                insert into section_academic_units (
                    semester_id, course_code, section_code, academic_unit_code,
                    relation_type, source_kind)
                values
                    ('2099-1', 'CSE100', '01', 'D1', 'OFFERING', 'CURRICULUM'),
                    ('2099-1', 'PHY100', '01', 'D2', 'OFFERING', 'CURRICULUM'),
                    ('2099-1', 'GEN100', '01', 'D2', 'OFFERING', 'CURRICULUM')
                """);
        // UNTAGGED100은 section_academic_units에 아무 행도 없다 (미분류).
    }

    @Test
    void unfilteredLookupReturnsEverySectionWithItsRestrictedAcademicUnits() {
        Map<SectionReference, AcademicSection> catalog =
                sectionQueryRepository.findBySemesterId("2099-1");

        assertThat(catalog).hasSize(4);
        assertThat(restrictedCodesOf(catalog, "CSE100")).containsExactly("D1");
        assertThat(restrictedCodesOf(catalog, "PHY100")).containsExactly("D2");
        assertThat(restrictedCodesOf(catalog, "GEN100")).isEmpty();
        assertThat(restrictedCodesOf(catalog, "UNTAGGED100")).isEmpty();
    }

    @Test
    void departmentFilteredLookupExcludesOtherDepartmentOnlySections() {
        Map<SectionReference, AcademicSection> catalog =
                sectionQueryRepository.findBySemesterId("2099-1", List.of("D1"));

        assertThat(courseCodesOf(catalog)).containsExactlyInAnyOrder(
                "CSE100", "GEN100", "UNTAGGED100");
    }

    private List<String> restrictedCodesOf(
            Map<SectionReference, AcademicSection> catalog, String courseCode) {
        return catalog.values().stream()
                .filter(section -> section.reference().getCourseCode().equals(courseCode))
                .findFirst()
                .orElseThrow()
                .restrictedAcademicUnitCodes();
    }

    private List<String> courseCodesOf(Map<SectionReference, AcademicSection> catalog) {
        return catalog.keySet().stream().map(SectionReference::getCourseCode).toList();
    }
}
