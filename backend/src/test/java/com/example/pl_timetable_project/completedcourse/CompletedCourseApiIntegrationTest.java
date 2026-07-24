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
