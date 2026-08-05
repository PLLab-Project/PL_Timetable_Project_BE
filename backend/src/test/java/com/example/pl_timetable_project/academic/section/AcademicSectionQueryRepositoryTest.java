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
                    ('2099-1', 'AREA100', '기초미적분학', '교양선택(제3영역:과학과기술)', 3),
                    ('2099-1', 'RESTRICT_PURE', '전산개론', '전공선택', 3),
                    ('2099-1', 'RESTRICT_GRADE', '전산개론2', '전공선택', 3),
                    ('2099-1', 'RESTRICT_TIME_OPEN', '전산개론3', '전공선택', 3),
                    ('2099-1', 'RESTRICT_NEGATION', '전산개론4', '전공선택', 3),
                    ('2099-1', 'RESTRICT_INTL', '전산개론5', '전공선택', 3),
                    ('2099-1', 'RESTRICT_DOUBLE_MAJOR', '전산개론6', '전공선택', 3)
                """);

        jdbcTemplate.update("""
                insert into sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes, notes)
                values
                    ('2099-1', 'CSE100', '01', '김교수', '', false, '[]', NULL),
                    ('2099-1', 'PHY100', '01', '이교수', '', false, '[]', NULL),
                    ('2099-1', 'GEN100', '01', '박교수', '', false, '[]', NULL),
                    ('2099-1', 'UNTAGGED100', '01', '최교수', '', false, '[]', NULL),
                    ('2099-1', 'AREA100', '01', '정교수', '', false, '[]', NULL),
                    ('2099-1', 'RESTRICT_PURE', '01', '강교수', '', false, '[]',
                        '컴퓨터공학과만수강가능'),
                    ('2099-1', 'RESTRICT_GRADE', '01', '강교수', '', false, '[]',
                        '컴퓨터공학과2학년만신청가능'),
                    ('2099-1', 'RESTRICT_TIME_OPEN', '01', '강교수', '', false, '[]',
                        '컴퓨터공학과만수강가능/셋째날11시해제'),
                    ('2099-1', 'RESTRICT_NEGATION', '01', '강교수', '', false, '[]',
                        '컴퓨터공학과우선신청/수강해제없음'),
                    ('2099-1', 'RESTRICT_INTL', '01', '강교수', '', false, '[]',
                        '컴퓨터공학과외국인유학생만수강가능'),
                    ('2099-1', 'RESTRICT_DOUBLE_MAJOR', '01', '강교수', '', false, '[]',
                        '컴퓨터공학과및복수전공생만신청가능')
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
        assertThat(catalog).hasSize(11);
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

    @Test
    void computesLiberalCreditFromCategoryContainingLiberalKeywordRegardlessOfAreaTag() {
        Map<SectionReference, AcademicSection> catalog =
                sectionQueryRepository.findBySemesterId("2099-1");

        // GEN100("교양선택")·AREA100("교양선택(제3영역:...)") 둘 다 교양 학점으로 잡힌다 —
        // liberalAreaCode(영역 표기가 있는 경우만)보다 넓은 판정이다.
        assertThat(catalog.values().stream()
                .filter(section -> section.reference().getCourseCode().equals("GEN100"))
                .findFirst().orElseThrow().liberalCredit()).isTrue();
        assertThat(catalog.values().stream()
                .filter(section -> section.reference().getCourseCode().equals("AREA100"))
                .findFirst().orElseThrow().liberalCredit()).isTrue();
        // 전공("CSE100")·미분류("UNTAGGED100")는 교양 학점이 아니다.
        assertThat(catalog.values().stream()
                .filter(section -> section.reference().getCourseCode().equals("CSE100"))
                .findFirst().orElseThrow().liberalCredit()).isFalse();
        assertThat(catalog.values().stream()
                .filter(section -> section.reference().getCourseCode().equals("UNTAGGED100"))
                .findFirst().orElseThrow().liberalCredit()).isFalse();
    }

    @Test
    void resolvesHardRestrictionOnlyForCleanEndAnchoredPatternWithoutExceptionKeywords() {
        Map<SectionReference, AcademicSection> catalog =
                sectionQueryRepository.findBySemesterId("2099-1");

        // (a) "학과명만수강가능"으로 정확히 끝나면 그 학과 코드로 확정된다.
        assertThat(hardRestrictedCodeOf(catalog, "RESTRICT_PURE")).isEqualTo("D1");
        // 학년 숫자가 껴 있어도(선택적 요소) 마찬가지로 확정된다.
        assertThat(hardRestrictedCodeOf(catalog, "RESTRICT_GRADE")).isEqualTo("D1");
        // (b) 뒤에 "/셋째날11시해제"가 붙으면(시간 개방 패턴) 제한으로 보지 않는다.
        assertThat(hardRestrictedCodeOf(catalog, "RESTRICT_TIME_OPEN")).isNull();
        // (c) "해제없음"처럼 부정어가 있어도,애초에 "만수강가능"으로 끝나지 않으므로
        // 안전하게 null로 남는다(오분류하지 않음).
        assertThat(hardRestrictedCodeOf(catalog, "RESTRICT_NEGATION")).isNull();
        // (d) 유학생/복수전공 같은 예외 키워드가 섞이면 패턴이 맞아도 제한으로 보지 않는다.
        assertThat(hardRestrictedCodeOf(catalog, "RESTRICT_INTL")).isNull();
        assertThat(hardRestrictedCodeOf(catalog, "RESTRICT_DOUBLE_MAJOR")).isNull();
        // notes가 아예 없거나 패턴과 무관한 분반도 당연히 null이다.
        assertThat(hardRestrictedCodeOf(catalog, "CSE100")).isNull();
    }

    private List<String> restrictedCodesOf(
            Map<SectionReference, AcademicSection> catalog, String courseCode) {
        return sectionOf(catalog, courseCode).restrictedAcademicUnitCodes();
    }

    private String liberalAreaCodeOf(
            Map<SectionReference, AcademicSection> catalog, String courseCode) {
        return sectionOf(catalog, courseCode).liberalAreaCode();
    }

    private String hardRestrictedCodeOf(
            Map<SectionReference, AcademicSection> catalog, String courseCode) {
        return sectionOf(catalog, courseCode).hardRestrictedAcademicUnitCode();
    }

    private AcademicSection sectionOf(
            Map<SectionReference, AcademicSection> catalog, String courseCode) {
        return catalog.values().stream()
                .filter(section -> section.reference().getCourseCode().equals(courseCode))
                .findFirst()
                .orElseThrow();
    }
}
