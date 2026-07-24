package com.example.pl_timetable_project.academic.graduation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
class GraduationApiIntegrationTest {

    private static final UUID USER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    WebApplicationContext applicationContext;

    @Autowired
    JdbcTemplate jdbcTemplate;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        insertFixture();
    }

    @Test
    void looksUpRuleByCohortUnitStudentTypeAndProgramPath()
            throws Exception {
        mockMvc.perform(get("/api/v1/graduation/rules")
                        .param("admissionYear", "2022")
                        .param("academicUnit", "컴공")
                        .param("studentType", "regular")
                        .param("programPath", "advanced_major"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value("credit-profile-1"))
                .andExpect(jsonPath("$.academicUnitCode").value("D1"))
                .andExpect(jsonPath("$.credits.total").value(20))
                .andExpect(jsonPath("$.liberalArts.totalMinimum").value(7))
                .andExpect(jsonPath("$.liberalAreas[0].area").value("소통"))
                .andExpect(jsonPath("$.requiredCourses.length()").value(4))
                .andExpect(jsonPath("$.sourceRefs[0]").value("official-credit-table.pdf#p=3"))
                .andExpect(jsonPath("$.warnings[0].code").value("SOURCE_TOTAL_MISMATCH"))
                .andExpect(jsonPath("$.nonAutomaticItems[0].code")
                        .value("PROFILE_MANUAL_REVIEW"))
                .andExpect(jsonPath(
                        "$.nonAutomaticItems[?(@.code == 'ASSESSMENT_A')]")
                        .exists());
    }

    @Test
    void evaluatesCompletedCreditsGapsRequiredCoursesAndRecommendations()
            throws Exception {
        mockMvc.perform(get("/api/v1/graduation/evaluation")
                        .with(authenticated())
                        .param("semesterId", "2026-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCredits.total").value(11))
                .andExpect(jsonPath("$.completedCredits.primaryMajor").value(6))
                .andExpect(jsonPath("$.completedCredits.liberalTotal").value(5))
                .andExpect(jsonPath("$.automaticRequirementsSatisfied").value(false))
                .andExpect(jsonPath("$.creditGaps[?(@.code == 'TOTAL')].missing")
                        .value(9.0))
                .andExpect(jsonPath("$.areaGaps[0].area").value("소통"))
                .andExpect(jsonPath("$.areaGaps[0].missingCourses").value(1))
                .andExpect(jsonPath("$.areaGaps[0].missingCredits").value(1))
                .andExpect(jsonPath("$.requiredCourseGaps.length()").value(2))
                .andExpect(jsonPath(
                        "$.requiredCourseGaps[?(@.course.courseCode == 'CSE202')]")
                        .exists())
                .andExpect(jsonPath(
                        "$.recommendations[?(@.courseCode == 'CSE202')].fills")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.hasItem("MAJOR_REQUIRED"))))
                .andExpect(jsonPath(
                        "$.recommendations[?(@.courseCode == 'CSE202')].fills")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.hasItem("PRIMARY_MAJOR"))))
                .andExpect(jsonPath(
                        "$.recommendations[?(@.courseCode == 'CSE202')].fills")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.hasItem("TOTAL"))))
                .andExpect(jsonPath(
                        "$.recommendations[?(@.courseCode == 'LIB102')]")
                        .exists())
                .andExpect(jsonPath("$.warnings[1].code").value(
                        "LIBERAL_AREA_RECOMMENDATION_REQUIRES_CATALOG_MAPPING"));
    }

    @Test
    void protectsPersonalEvaluationAndValidatesRuleScope()
            throws Exception {
        mockMvc.perform(get("/api/v1/graduation/evaluation"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/graduation/rules")
                        .param("admissionYear", "2022")
                        .param("academicUnit", "컴퓨터공학과")
                        .param("studentType", "REGULAR")
                        .param("programPath", "UNSUPPORTED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACADEMIC_QUERY"));
    }

    private RequestPostProcessor authenticated() {
        AuthenticatedUser principal =
                new AuthenticatedUser(USER_ID, "20220001");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return authentication(authentication);
    }

    private void insertFixture() {
        jdbcTemplate.execute("""
                INSERT INTO academic_colleges (
                    code, name, first_seen_year, last_seen_year, is_current
                ) VALUES (
                    'C1', '공과대학', 2020, 2026, true
                );

                INSERT INTO academic_units (
                    code, college_code, name, code_source,
                    first_seen_year, last_seen_year, is_current
                ) VALUES (
                    'D1', 'C1', '컴퓨터공학과', 'OFFICIAL_CURRICULUM',
                    2020, 2026, true
                );

                INSERT INTO academic_unit_aliases (
                    academic_unit_code, alias, valid_from_year, valid_to_year,
                    source_kind, is_primary
                ) VALUES (
                    'D1', '컴공', 2020, NULL, 'CURRICULUM', false
                );

                INSERT INTO requirement_datasets (
                    id, kind, schema_version, admission_year, effective_year,
                    as_of, source_path, source_checksum, normalized_checksum,
                    record_count, raw_payload, imported_at
                ) VALUES (
                    'graduation-test-dataset', 'GRADUATION', '1', 2022, 2022,
                    DATE '2026-07-24', 'fixtures/graduation-source.json',
                    repeat('a', 64), repeat('b', 64), 10, '{}'::json,
                    now()
                );

                INSERT INTO graduation_liberal_requirement_sets (
                    id, dataset_id, signature, admission_year, student_type,
                    required_credits_min, elective_credits_min,
                    total_credits_min, total_credits_max
                ) VALUES (
                    'liberal-set-1', 'graduation-test-dataset',
                    repeat('c', 64), 2022, 'REGULAR', 4, 3, 7, 12
                );

                INSERT INTO graduation_credit_profiles (
                    id, dataset_id, source_rule_id,
                    liberal_requirement_set_id, academic_unit,
                    academic_unit_key, admission_year, student_type,
                    program_path, total_credits_min, major_foundation_min,
                    major_required_min, major_elective_min,
                    additional_major_min, primary_major_min,
                    secondary_program_min, requires_manual_review,
                    academic_unit_code
                ) VALUES (
                    'credit-profile-1', 'graduation-test-dataset',
                    'credit-source-rule-1', 'liberal-set-1',
                    '컴퓨터공학과', '컴퓨터공학과', 2022, 'REGULAR',
                    'ADVANCED_MAJOR', 20, 3, 6, 3, NULL, 12, NULL, true,
                    'D1'
                );

                INSERT INTO graduation_credit_profile_academic_unit_aliases (
                    profile_id, "position", alias, alias_key
                ) VALUES (
                    'credit-profile-1', 0, '컴공', '컴공'
                );

                INSERT INTO graduation_credit_profile_source_refs (
                    profile_id, "position", source_ref
                ) VALUES (
                    'credit-profile-1', 0, 'official-credit-table.pdf#p=3'
                );

                INSERT INTO graduation_credit_profile_warnings (
                    id, profile_id, "position", code, calculated, printed
                ) VALUES (
                    'credit-warning-1', 'credit-profile-1', 0,
                    'SOURCE_TOTAL_MISMATCH', 20, 19
                );

                INSERT INTO graduation_liberal_area_requirements (
                    id, requirement_set_id, "position", area,
                    min_courses, min_credits
                ) VALUES (
                    'area-rule-1', 'liberal-set-1', 0, '소통', 3, 6
                );

                INSERT INTO graduation_liberal_required_courses (
                    id, requirement_set_id, "position", course_code,
                    course_name, credits, grade, source_page
                ) VALUES
                    (
                        'lib-course-1', 'liberal-set-1', 0, 'LIB101',
                        '대학글쓰기', 2, 1, 4
                    ),
                    (
                        'lib-course-2', 'liberal-set-1', 1, 'LIB102',
                        '발표와토론', 2, 1, 4
                    );

                INSERT INTO graduation_liberal_course_aliases (
                    id, course_id, "position", alias, alias_key
                ) VALUES (
                    'lib-alias-1', 'lib-course-2', 0, '프레젠테이션과토론',
                    '프레젠테이션과토론'
                );

                INSERT INTO curriculum_program_requirements (
                    id, dataset_id, admission_year, academic_unit,
                    academic_unit_key, status, source_locators,
                    source_course_count, required_course_count, raw_payload,
                    academic_unit_code
                ) VALUES (
                    'curriculum-program-1', 'graduation-test-dataset', 2022,
                    '컴퓨터공학과', '컴퓨터공학과', 'NORMALIZED',
                    '[]'::json, 2, 2, '{}'::json, 'D1'
                );

                INSERT INTO curriculum_required_courses (
                    id, program_id, classification, course_code, course_name,
                    credits, grade, semesters, source_locator, raw_payload
                ) VALUES
                    (
                        'major-course-1', 'curriculum-program-1', '전필',
                        'CSE201', '자료구조', 3, 2, '[1]'::json,
                        '{"page": 10}'::json, '{}'::json
                    ),
                    (
                        'major-course-2', 'curriculum-program-1', '전필',
                        'CSE202', '알고리즘', 3, 2, '[2]'::json,
                        '{"page": 10}'::json, '{}'::json
                    );

                INSERT INTO graduation_assessment_profiles (
                    id, dataset_id, source_rule_id, effective_year,
                    academic_unit, academic_unit_key, transition_mode,
                    transition_source_text, source_note,
                    requires_manual_review, academic_unit_code
                ) VALUES (
                    'assessment-profile-1', 'graduation-test-dataset',
                    'assessment-rule-1', 2026, '컴퓨터공학과',
                    '컴퓨터공학과', 'STANDARDIZED_ONLY', '표준화 판정',
                    '학과 확인 필요', true, 'D1'
                );

                INSERT INTO graduation_assessment_categories (
                    id, profile_id, category_code, category_name,
                    requirement_detail, reference_note, source_note
                ) VALUES (
                    'assessment-category-1', 'assessment-profile-1',
                    'A', '외국어 능력', '공인 점수 확인', '학과 내규',
                    '자동 판정 불가'
                );

                INSERT INTO graduation_assessment_source_refs (
                    profile_id, "position", source_ref
                ) VALUES (
                    'assessment-profile-1', 0,
                    'official-assessment.pdf#p=2'
                );

                INSERT INTO graduation_requirement_rules (
                    id, dataset_id, rule_kind, category_code,
                    academic_unit, academic_unit_key,
                    admission_year_start, admission_year_end,
                    effective_year, student_type, program_path,
                    description, requires_manual_review, raw_payload,
                    academic_unit_code
                ) VALUES (
                    'manual-thesis-rule', 'graduation-test-dataset',
                    'THESIS', 'S', '컴퓨터공학과', '컴퓨터공학과',
                    2022, 2022, 2022, 'REGULAR', 'ADVANCED_MAJOR',
                    '졸업논문 제출 여부 확인', true, '{}'::json, 'D1'
                );

                INSERT INTO users (
                    id, display_name, primary_email
                ) VALUES (
                    '10000000-0000-0000-0000-000000000001',
                    '졸업판정학생', 'graduation@example.com'
                );

                INSERT INTO student_profiles (
                    user_id, student_number, academic_unit_name,
                    academic_unit_key, grade, admission_year, entry_type,
                    student_type, section_group, program_path,
                    profile_completed
                ) VALUES (
                    '10000000-0000-0000-0000-000000000001',
                    '20220001', '컴퓨터공학과', '컴퓨터공학과', 4, 2022,
                    'NEW', 'REGULAR', 'DAY', 'ADVANCED_MAJOR', true
                );

                INSERT INTO completed_courses (
                    user_id, course_code, course_name, credits, category,
                    area, semester, status, input_source
                ) VALUES
                    (
                        '10000000-0000-0000-0000-000000000001',
                        'CSE101', '컴퓨터개론', 3, '전공기초', NULL,
                        '2022-1', 'COMPLETED', 'MANUAL'
                    ),
                    (
                        '10000000-0000-0000-0000-000000000001',
                        'CSE201', '자료구조', 3, '전공필수', NULL,
                        '2023-1', 'COMPLETED', 'MANUAL'
                    ),
                    (
                        '10000000-0000-0000-0000-000000000001',
                        'LIB101', '대학글쓰기', 2, '교양필수', '소통',
                        '2022-1', 'COMPLETED', 'MANUAL'
                    ),
                    (
                        '10000000-0000-0000-0000-000000000001',
                        'LIB201', '비판적사고', 3, '교양선택', '소통',
                        '2023-2', 'COMPLETED', 'MANUAL'
                    );

                INSERT INTO semesters (
                    id, prepared_at, dataset_version, source_checksum,
                    is_active, created_at
                ) VALUES (
                    '2026-1', DATE '2026-07-24', 'graduation-test-v1',
                    repeat('d', 64), true, now()
                );

                INSERT INTO courses (
                    semester_id, course_code, name, category, credits
                ) VALUES
                    ('2026-1', 'CSE202', '알고리즘', '전공필수', 3),
                    ('2026-1', 'CSE300', '운영체제', '전공선택', 3),
                    ('2026-1', 'LIB102', '발표와토론', '교양필수', 2);

                INSERT INTO sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes
                ) VALUES
                    ('2026-1', 'CSE202', '01', '김교수', '월1', false, '[]'::jsonb),
                    ('2026-1', 'CSE300', '01', '이교수', '화1', false, '[]'::jsonb),
                    ('2026-1', 'LIB102', '01', '박교수', '수1', false, '[]'::jsonb);

                INSERT INTO section_academic_units (
                    semester_id, course_code, section_code,
                    academic_unit_code, relation_type, source_kind
                ) VALUES
                    (
                        '2026-1', 'CSE202', '01', 'D1',
                        'OFFERING', 'CURRICULUM'
                    ),
                    (
                        '2026-1', 'CSE300', '01', 'D1',
                        'OFFERING', 'CURRICULUM'
                    );
                """);
    }
}
