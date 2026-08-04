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
 * findBySemesterId가 학과와 무관하게 모든 분반을 반환하면서도, section_academic_units와
 * 과목 category를 기준으로 각 분반의 restrictedAcademicUnitCodes(본인 학과 가중치
 * 판단에 쓰이는 구조화된 학과 태그)를 정확히 계산하는지 검증한다.
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
                    ('2099-1', 'UNTAGGED100', '미분류 전공선택', '전공선택', 3),
                    ('2099-1', 'AREA100', '기초미적분학', '교양선택(제3영역:과학과기술)', 3)
                """);

        jdbcTemplate.update("""
                insert into sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes)
                values
                    ('2099-1', 'CSE100', '01', '김교수', '', false, '[]'),
                    ('2099-1', 'PHY100', '01', '이교수', '', false, '[]'),
                    ('2099-1', 'GEN100', '01', '박교수', '', false, '[]'),
                    ('2099-1', 'UNTAGGED100', '01', '최교수', '', false, '[]'),
                    ('2099-1', 'AREA100', '01', '정교수', '', false, '[]')
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
    void returnsEverySectionRegardlessOfDepartmentWithCorrectRestrictedAcademicUnits() {
        Map<SectionReference, AcademicSection> catalog =
                sectionQueryRepository.findBySemesterId("2099-1");

        // 학과 필터로 배제된 분반이 없어야 한다 — PHY100(D2 전용)도 그대로 포함된다.
        assertThat(catalog).hasSize(5);
        assertThat(restrictedCodesOf(catalog, "CSE100")).containsExactly("D1");
        assertThat(restrictedCodesOf(catalog, "PHY100")).containsExactly("D2");
        assertThat(restrictedCodesOf(catalog, "GEN100")).isEmpty();
        assertThat(restrictedCodesOf(catalog, "UNTAGGED100")).isEmpty();
    }

    @Test
    void parsesLiberalAreaCodeOnlyFromTheDedicatedCategoryPattern() {
        Map<SectionReference, AcademicSection> catalog =
                sectionQueryRepository.findBySemesterId("2099-1");

        // "교양선택(제3영역:과학과기술)"처럼 패턴이 정확히 맞을 때만 파싱한다.
        assertThat(liberalAreaCodeOf(catalog, "AREA100")).isEqualTo("제3영역:과학과기술");
        // 전공, 괄호 없는 교양선택, 미분류 등은 전부 null이어야 한다(방어적 처리).
        assertThat(liberalAreaCodeOf(catalog, "CSE100")).isNull();
        assertThat(liberalAreaCodeOf(catalog, "GEN100")).isNull();
        assertThat(liberalAreaCodeOf(catalog, "UNTAGGED100")).isNull();
    }

    private List<String> restrictedCodesOf(
            Map<SectionReference, AcademicSection> catalog, String courseCode) {
        return sectionOf(catalog, courseCode).restrictedAcademicUnitCodes();
    }

    private String liberalAreaCodeOf(
            Map<SectionReference, AcademicSection> catalog, String courseCode) {
        return sectionOf(catalog, courseCode).liberalAreaCode();
    }

    private AcademicSection sectionOf(
            Map<SectionReference, AcademicSection> catalog, String courseCode) {
        return catalog.values().stream()
                .filter(section -> section.reference().getCourseCode().equals(courseCode))
                .findFirst()
                .orElseThrow();
    }
}
