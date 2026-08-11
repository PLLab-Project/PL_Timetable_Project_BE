package com.example.pl_timetable_project.completedcourse;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
class CompletedCourseApiIntegrationTest {

    private static final UUID USER_ONE =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_TWO =
            UUID.fromString("10000000-0000-0000-0000-000000000002");

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
        insertUsers();
    }

    @Test
    void supportsAuthenticatedCrudAndEnforcesOwnership() throws Exception {
        String createdBody = mockMvc.perform(post("/api/v1/completed-courses")
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseCode": "CSE100",
                                  "courseName": "자료구조",
                                  "credits": 3.00,
                                  "category": "전공필수",
                                  "area": "전공핵심",
                                  "semester": "2026-1",
                                  "status": "IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseCode").value("CSE100"))
                .andExpect(jsonPath("$.data.inputSource").value("MANUAL"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID completedCourseId = UUID.fromString(JsonPath.read(createdBody, "$.data.id"));

        mockMvc.perform(get("/api/v1/completed-courses")
                        .with(authenticatedAs(USER_ONE))
                        .param("status", "IN_PROGRESS")
                        .param("semester", "2026-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(completedCourseId.toString()))
                .andExpect(jsonPath("$.data[0].area").value("전공핵심"));

        mockMvc.perform(patch("/api/v1/completed-courses/{id}", completedCourseId)
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseName": "자료구조와실습",
                                  "area": "전공심화"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseName").value("자료구조와실습"))
                .andExpect(jsonPath("$.data.area").value("전공심화"));

        mockMvc.perform(get("/api/v1/completed-courses/{id}", completedCourseId)
                        .with(authenticatedAs(USER_TWO)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPLETED_COURSE_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/completed-courses/{id}", completedCourseId)
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(get("/api/v1/completed-courses/{id}", completedCourseId)
                        .with(authenticatedAs(USER_ONE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void summarizesCreditsAndTransitionsOnlyInProgressCourses() throws Exception {
        UUID inProgressId = UUID.randomUUID();
        insertCompletedCourse(
                inProgressId, USER_ONE, "CSE200", "알고리즘", "3.00",
                "전공선택", "전공심화", "IN_PROGRESS");
        insertCompletedCourse(
                UUID.randomUUID(), USER_ONE, "GEN100", "글쓰기", "2.00",
                "교양필수", "의사소통", "COMPLETED");
        insertCompletedCourse(
                UUID.randomUUID(), USER_TWO, "OTHER", "타인과목", "9.00",
                "전공선택", "전공심화", "COMPLETED");

        mockMvc.perform(post("/api/v1/completed-courses/{id}/complete", inProgressId)
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/completed-courses/summary")
                        .with(authenticatedAs(USER_ONE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCredits").value(5.0))
                .andExpect(jsonPath("$.data.completedCredits").value(5.0))
                .andExpect(jsonPath("$.data.inProgressCredits").value(0))
                .andExpect(jsonPath("$.data.creditsByCategory['전공선택']").value(3.0))
                .andExpect(jsonPath("$.data.creditsByArea['의사소통']").value(2.0))
                .andExpect(jsonPath("$.data.creditsByStatus.COMPLETED").value(5.0));

        mockMvc.perform(post("/api/v1/completed-courses/{id}/complete", inProgressId)
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("COMPLETED_COURSE_INVALID_STATUS_TRANSITION"));
    }

    @Test
    void storesPassFailGradeSeparatelyFromRecognizedCredits() throws Exception {
        mockMvc.perform(post("/api/v1/completed-courses")
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseCode": "GEN-PN",
                                  "courseName": "봉사활동",
                                  "credits": 2.00,
                                  "gradingBasis": "PASS_FAIL",
                                  "gradeValue": "p",
                                  "category": "교양선택",
                                  "status": "COMPLETED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.credits").value(2.0))
                .andExpect(jsonPath("$.data.gradingBasis").value("PASS_FAIL"))
                .andExpect(jsonPath("$.data.gradeValue").value("P"));

        mockMvc.perform(post("/api/v1/completed-courses")
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseName": "잘못된 P/N",
                                  "credits": 1.00,
                                  "gradingBasis": "PASS_FAIL",
                                  "gradeValue": "A+",
                                  "category": "교양선택",
                                  "status": "COMPLETED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPLETED_COURSE_INVALID_REQUEST"));
    }

    @Test
    void storesConfirmedOcrCourseUsingHistoricalOfferingReference() throws Exception {
        insertHistoricalOfferingFixture();

        mockMvc.perform(post("/api/v1/completed-courses")
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseCode": "OCR-TYPO",
                                  "courseName": "컴퓨팅 사고와 문제 해결",
                                  "credits": 3.00,
                                  "category": "교양선택",
                                  "semester": "2026-1",
                                  "status": "COMPLETED",
                                  "historicalOfferingId": "history-2020-1-927313-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseCode").value("927313"))
                .andExpect(jsonPath("$.data.courseName").value("컴퓨팅사고와문제해결"))
                .andExpect(jsonPath("$.data.credits").value(2.0))
                .andExpect(jsonPath("$.data.category").value("교양필수"))
                .andExpect(jsonPath("$.data.semester").value("2020-1"))
                .andExpect(jsonPath("$.data.historicalOfferingId")
                        .value("history-2020-1-927313-01"))
                .andExpect(jsonPath("$.data.offeringId")
                        .value("2020-1:927313:01"))
                .andExpect(jsonPath("$.data.sectionCode").value("01"))
                .andExpect(jsonPath("$.data.inputSource").value("OCR"))
                .andExpect(jsonPath("$.data.sourceSnapshot.professorName").value("김지연"));
    }

    @Test
    void storesDirectCatalogSelectionUsingUnifiedOfferingId() throws Exception {
        insertHistoricalOfferingFixture();

        mockMvc.perform(post("/api/v1/completed-courses")
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseName": "사용자 오타",
                                  "credits": 1.00,
                                  "category": "교양선택",
                                  "status": "COMPLETED",
                                  "offeringId": "2020-1:927313:01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseCode").value("927313"))
                .andExpect(jsonPath("$.data.courseName").value("컴퓨팅사고와문제해결"))
                .andExpect(jsonPath("$.data.credits").value(2.0))
                .andExpect(jsonPath("$.data.category").value("교양필수"))
                .andExpect(jsonPath("$.data.offeringId")
                        .value("2020-1:927313:01"))
                .andExpect(jsonPath("$.data.inputSource").value("CATALOG"));
    }

    @Test
    void importsOwnedTimetableSectionsIdempotentlyAsInProgress() throws Exception {
        Long timetableId = insertTimetableFixture();

        mockMvc.perform(post("/api/v1/completed-courses/imports/timetables/{id}", timetableId)
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(0))
                .andExpect(jsonPath("$.data.records[0].courseCode").value("CSE300"))
                .andExpect(jsonPath("$.data.records[0].category").value("전공선택"))
                .andExpect(jsonPath("$.data.records[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.records[0].inputSource").value("TIMETABLE"))
                .andExpect(jsonPath("$.data.records[0].sourceSnapshot.timetableId")
                        .value(timetableId));

        mockMvc.perform(post("/api/v1/completed-courses/imports/timetables/{id}", timetableId)
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.importedCount").value(0))
                .andExpect(jsonPath("$.data.skippedCount").value(1));

        mockMvc.perform(post("/api/v1/completed-courses/imports/timetables/{id}", timetableId)
                        .with(authenticatedAs(USER_TWO))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("COMPLETED_COURSE_TIMETABLE_NOT_FOUND"));
    }

    @Test
    void rejectsUnauthenticatedAccessAndInvalidPayloads() throws Exception {
        mockMvc.perform(get("/api/v1/completed-courses"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/completed-courses")
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseName": " ",
                                  "credits": -1,
                                  "category": "전공필수",
                                  "status": "COMPLETED"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/completed-courses")
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseName": "자료구조",
                                  "credits": 3,
                                  "category": "전공필수",
                                  "status": "DONE"
                                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));
    }

    /**
     * 프론트는 교양선택 세부 영역을 "과학과 기술"(표시용 이름, 띄어쓰기 있음,
     * 영역 번호 없음) 형식으로 보낸다. 백엔드는 이걸 졸업요건 계산이 쓰는 내부
     * 코드 "제3영역:과학과기술"로 정규화해 저장해야 한다 — 그래야
     * GraduationProgressCalculator.areaGaps()가 graduation_liberal_area_
     * requirements.area와 문자열로 비교할 때 맞는다. 이미 코드 형식으로 온
     * 값은 그대로 유지되고(멱등), "전공심화"처럼 교양 영역이 아닌 값은 원본
     * 그대로 저장된다(회귀 없음, supportsAuthenticatedCrudAndEnforcesOwnership에서
     * 이미 확인).
     */
    @Test
    void normalizesLiberalAreaDisplayLabelToInternalCode() throws Exception {
        String createdBody = mockMvc.perform(post("/api/v1/completed-courses")
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "courseName": "물리학과 세상보기",
                                  "credits": 3.00,
                                  "category": "교양선택",
                                  "area": "과학과 기술",
                                  "semester": "2026-1",
                                  "status": "COMPLETED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.area").value("제3영역:과학과기술"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID completedCourseId = UUID.fromString(JsonPath.read(createdBody, "$.data.id"));

        mockMvc.perform(patch("/api/v1/completed-courses/{id}", completedCourseId)
                        .with(authenticatedAs(USER_ONE))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "area": "제5영역:융합과혁신"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.area").value("제5영역:융합과혁신"));
    }

    private RequestPostProcessor authenticatedAs(UUID userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "20260001");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of());
        return authentication(authentication);
    }

    private void insertUsers() {
        jdbcTemplate.update(
                "INSERT INTO users (id, display_name, primary_email) VALUES (?, ?, ?)",
                USER_ONE,
                "사용자1",
                "completed-one@example.com");
        jdbcTemplate.update(
                "INSERT INTO users (id, display_name, primary_email) VALUES (?, ?, ?)",
                USER_TWO,
                "사용자2",
                "completed-two@example.com");
    }

    private void insertCompletedCourse(
            UUID id,
            UUID userId,
            String courseCode,
            String courseName,
            String credits,
            String category,
            String area,
            String status) {
        jdbcTemplate.update(
                """
                INSERT INTO completed_courses (
                    id, user_id, course_code, course_name, credits,
                    category, area, semester, status, input_source
                ) VALUES (?, ?, ?, ?, ?::numeric, ?, ?, '2026-1', ?, 'MANUAL')
                """,
                id,
                userId,
                courseCode,
                courseName,
                credits,
                category,
                area,
                status);
    }

    private void insertHistoricalOfferingFixture() {
        jdbcTemplate.execute("""
                INSERT INTO historical_term_datasets (
                    id, academic_year, term_code, term_name, data_status,
                    schema_version, collected_at, source_checksum, record_count,
                    raw_payload, source_archive, imported_at
                ) VALUES (
                    'completed-term-2020', 2020, '1', '1학기', 'COMPLETE', '1',
                    now(), repeat('e', 64), 1, '{}'::json, decode('', 'hex'), now()
                );

                INSERT INTO historical_course_offerings (
                    id, dataset_id, academic_year, term_code, course_code,
                    section_code, korean_name, english_name, professor_name,
                    completion_category, credits, lecture_hours, practice_hours,
                    raw_lecture_time, raw_location, target_grade, listing_status,
                    detail_status, category_contexts, department_contexts,
                    search_text, department_search_text, raw_payload
                ) VALUES (
                    'history-2020-1-927313-01', 'completed-term-2020',
                    2020, '1', '927313', '01', '컴퓨팅사고와문제해결', NULL,
                    '김지연', '교필', 2, 2, 0, '수11:30-13:30',
                    '정보 310 스마트강의실', NULL, 'LISTED', 'AVAILABLE',
                    '[]'::json, '[]'::json, '컴퓨팅사고와문제해결', '', '{}'::json
                );
                """);
        jdbcTemplate.execute("SELECT reconcile_historical_course_offerings()");
    }

    private Long insertTimetableFixture() {
        jdbcTemplate.execute("""
                INSERT INTO semesters (
                    id, prepared_at, dataset_version, source_checksum, is_active, created_at
                ) VALUES (
                    '2026-1', DATE '2026-07-24', 'completed-course-test-v1',
                    repeat('c', 64), true, now()
                );

                INSERT INTO courses (
                    semester_id, course_code, name, category, credits
                ) VALUES (
                    '2026-1', 'CSE300', '운영체제', '전공선택', 3.00
                );

                INSERT INTO sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes
                ) VALUES (
                    '2026-1', 'CSE300', '01', '김교수',
                    '수1-2', false, '[]'::jsonb
                );
                """);
        Long timetableId = jdbcTemplate.queryForObject(
                """
                INSERT INTO timetables (user_id, semester_id, name)
                VALUES (?, '2026-1', '현재 시간표')
                RETURNING id
                """,
                Long.class,
                USER_ONE);
        jdbcTemplate.update(
                """
                INSERT INTO timetable_courses (
                    timetable_id, semester_id, course_code, section_code,
                    course_name, professor_name, credits
                ) VALUES (?, '2026-1', 'CSE300', '01', '운영체제', '김교수', 3.00)
                """,
                timetableId);
        return timetableId;
    }
}
