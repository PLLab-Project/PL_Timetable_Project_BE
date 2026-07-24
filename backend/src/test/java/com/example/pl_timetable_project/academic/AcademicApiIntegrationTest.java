package com.example.pl_timetable_project.academic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class AcademicApiIntegrationTest {

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
    void allowsPublicAcademicReadsAndProtectsUserTimetables() throws Exception {
        mockMvc.perform(get("/api/v1/semesters"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/timetables"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsCurrentDepartmentsWithAliasesAndPagination() throws Exception {
        mockMvc.perform(get("/api/v1/departments")
                        .param("query", "컴퓨터")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value("D1"))
                .andExpect(jsonPath("$.data.items[0].collegeName").value("공과대학"));

        mockMvc.perform(get("/api/v1/departments/D1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("컴퓨터공학과"))
                .andExpect(jsonPath("$.data.aliases[0].alias").value("컴공"))
                .andExpect(jsonPath("$.data.aliases[0].primary").value(true));
    }

    @Test
    void returnsSemesterAndDatasetVersion() throws Exception {
        mockMvc.perform(get("/api/v1/semesters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("2026-1"))
                .andExpect(jsonPath("$.data[0].datasetVersion").value("academic-api-test-v1"));

        mockMvc.perform(get("/api/v1/semesters/2026-1/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.semesterId").value("2026-1"))
                .andExpect(jsonPath("$.data.sourceChecksum")
                        .value("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }

    @Test
    void searchesAndFiltersCoursesUsingCanonicalAcademicData() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                        .param("semesterId", "2026-1")
                        .param("query", "자료")
                        .param("academicUnitCode", "D1")
                        .param("day", "MONDAY")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].courseCode").value("CSE100"))
                .andExpect(jsonPath("$.data.items[0].sectionCount").value(1))
                .andExpect(jsonPath("$.data.items[0].ratingAverage").value(4.5))
                .andExpect(jsonPath("$.data.items[0].reviewCount").value(2));

        mockMvc.perform(get("/api/v1/courses/2026-1/CSE100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("자료구조"))
                .andExpect(jsonPath("$.data.academicUnits[0].code").value("D1"));

        mockMvc.perform(get("/api/v1/courses")
                        .param("semesterId", "2026-1")
                        .param("category", "전공필수")
                        .param("professor", "홍")
                        .param("credits", "3.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].courseCode").value("CSE100"));
    }

    @Test
    void supportsRatingAndPopularitySorts() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                        .param("semesterId", "2026-1")
                        .param("sort", "NAME_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].courseCode").value("CSE100"))
                .andExpect(jsonPath("$.data.items[1].courseCode").value("CSE200"))
                .andExpect(jsonPath("$.data.items[2].courseCode").value("GEN100"));

        mockMvc.perform(get("/api/v1/courses")
                        .param("semesterId", "2026-1")
                        .param("sort", "REVIEW_COUNT_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].courseCode").value("CSE100"))
                .andExpect(jsonPath("$.data.items[1].courseCode").value("CSE200"))
                .andExpect(jsonPath("$.data.items[2].courseCode").value("GEN100"));

        mockMvc.perform(get("/api/v1/courses")
                        .param("semesterId", "2026-1")
                        .param("sort", "RATING_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].courseCode").value("CSE200"))
                .andExpect(jsonPath("$.data.items[0].reviewCount").value(1))
                .andExpect(jsonPath("$.data.items[0].ratingAverage").value(5.0));

        mockMvc.perform(get("/api/v1/courses")
                        .param("semesterId", "2026-1")
                        .param("sort", "POPULARITY_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].courseCode").value("CSE100"))
                .andExpect(jsonPath("$.data.items[0].reviewCount").value(2))
                .andExpect(jsonPath("$.data.items[0].ratingAverage").value(4.5));
    }

    @Test
    void exposesPublicReviewListsWithoutAuthorIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/courses/reviews")
                        .param("semesterId", "2026-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].userId").doesNotExist());

        mockMvc.perform(get(
                        "/api/v1/courses/reviews/{courseCode}",
                        "CSE100")
                        .param("semesterId", "2026-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].courseCode").value("CSE100"))
                .andExpect(jsonPath("$.data.items[1].courseCode").value("CSE100"));

        mockMvc.perform(get(
                        "/api/v1/courses/reviews/{courseCode}/professors/{professor}",
                        "CSE100",
                        "홍길동")
                        .param("semesterId", "2026-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void createsListsUpdatesAndDeletesOwnedReviews() throws Exception {
        String userId = "00000000-0000-0000-0000-000000000003";
        mockMvc.perform(post("/api/v1/reviews")
                        .with(signedInAs(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "semesterId": "2026-1",
                                  "courseCode": "GEN100",
                                  "rating": 4,
                                  "content": "글쓰기 연습에 도움이 됩니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.courseCode").value("GEN100"))
                .andExpect(jsonPath("$.data.courseName").value("글쓰기"))
                .andExpect(jsonPath("$.data.professor").isEmpty())
                .andExpect(jsonPath("$.data.rating").value(4));

        UUID reviewId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM course_reviews
                 WHERE user_id = ?::uuid
                   AND course_code = 'GEN100'
                """, UUID.class, userId);

        mockMvc.perform(get("/api/v1/reviews/me")
                        .with(signedInAs(userId))
                        .param("semesterId", "2026-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(patch("/api/v1/reviews/{reviewId}", reviewId)
                        .with(signedInAs(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "content": "수정한 리뷰입니다."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.content").value("수정한 리뷰입니다."));

        mockMvc.perform(get("/api/v1/courses/2026-1/GEN100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(jsonPath("$.data.ratingAverage").value(5.0));

        mockMvc.perform(delete("/api/v1/reviews/{reviewId}", reviewId)
                        .with(signedInAs(userId))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(get("/api/v1/reviews/me")
                        .with(signedInAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void enforcesReviewAuthenticationValidationDuplicatesAndOwnership()
            throws Exception {
        String userOne = "00000000-0000-0000-0000-000000000001";
        String userTwo = "00000000-0000-0000-0000-000000000002";
        String duplicateReview = """
                {
                  "semesterId": "2026-1",
                  "courseCode": "CSE100",
                  "professor": "홍길동",
                  "rating": 3,
                  "content": "중복 리뷰"
                }
                """;

        mockMvc.perform(post("/api/v1/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateReview))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/reviews/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/reviews")
                        .with(signedInAs(userOne))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateReview))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACADEMIC_QUERY"));

        mockMvc.perform(post("/api/v1/reviews")
                        .with(signedInAs(userTwo))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "semesterId": "2026-1",
                                  "courseCode": "CSE100",
                                  "professor": "없는교수",
                                  "rating": 3,
                                  "content": "교수 검증"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACADEMIC_QUERY"));

        mockMvc.perform(post("/api/v1/reviews")
                        .with(signedInAs(userTwo))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "semesterId": "2026-1",
                                  "courseCode": "GEN100",
                                  "rating": 0,
                                  "content": "별점 검증"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        UUID userOneReviewId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM course_reviews
                 WHERE user_id = ?::uuid
                   AND course_code = 'CSE100'
                """, UUID.class, userOne);
        mockMvc.perform(patch(
                        "/api/v1/reviews/{reviewId}", userOneReviewId)
                        .with(signedInAs(userTwo))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 1,
                                  "content": "다른 사용자 리뷰 수정 시도"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("ACADEMIC_RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsSectionSessionsAndValidatedErrors() throws Exception {
        mockMvc.perform(get("/api/v1/courses/2026-1/CSE100/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sectionCode").value("01"))
                .andExpect(jsonPath("$.data[0].sessions[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data[0].sessions[0].roomLabel").value("공학관 101호"));

        mockMvc.perform(get("/api/v1/courses/2026-1/CSE100/sections/01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warningCodes[0]").value("SOURCE_TIME_NORMALIZED"))
                .andExpect(jsonPath("$.data.academicUnits[0].code").value("D1"));

        mockMvc.perform(get("/api/v1/courses")
                        .param("semesterId", "2026-1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACADEMIC_QUERY"));

        mockMvc.perform(get("/api/v1/courses")
                        .param("semesterId", "2026-1")
                        .param("credits", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/courses"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/courses")
                        .param("semesterId", "2026-1")
                        .param("sort", "UNSUPPORTED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACADEMIC_QUERY"));

        mockMvc.perform(get("/api/v1/courses")
                        .param("semesterId", "2026-1")
                        .param("day", "HOLIDAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACADEMIC_QUERY"));

        mockMvc.perform(get("/api/v1/courses/2026-1/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACADEMIC_RESOURCE_NOT_FOUND"));
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
                ) VALUES
                    ('D1', 'C1', '컴퓨터공학과', 'OFFICIAL_CURRICULUM', 2020, 2026, true),
                    ('D-OLD', 'C1', '구컴퓨터공학과', 'OFFICIAL_CURRICULUM', 2016, 2019, false),
                    ('REQ-TEST', NULL, '요건파생전공', 'REQUIREMENT_DERIVED', 2020, 2026, true);

                INSERT INTO academic_unit_aliases (
                    academic_unit_code, alias, valid_from_year, valid_to_year,
                    source_kind, is_primary
                ) VALUES (
                    'D1', '컴공', 2020, NULL, 'CURRICULUM', true
                );

                INSERT INTO semesters (
                    id, prepared_at, dataset_version, source_checksum, is_active, created_at
                ) VALUES (
                    '2026-1', DATE '2026-07-20', 'academic-api-test-v1',
                    repeat('a', 64), true, now()
                );

                INSERT INTO courses (
                    semester_id, course_code, name, category, credits
                ) VALUES
                    ('2026-1', 'CSE100', '자료구조', '전공필수', 3.00),
                    ('2026-1', 'CSE200', '알고리즘', '전공선택', 3.00),
                    ('2026-1', 'GEN100', '글쓰기', '교양필수', 2.00);

                INSERT INTO sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes
                ) VALUES
                    (
                        '2026-1', 'CSE100', '01', '홍길동',
                        '월1-2', false, '["SOURCE_TIME_NORMALIZED"]'::jsonb
                    ),
                    (
                        '2026-1', 'CSE200', '01', '김교수',
                        '화3-4', false, '[]'::jsonb
                    ),
                    (
                        '2026-1', 'GEN100', '01', NULL,
                        '미정', true, '[]'::jsonb
                    );

                INSERT INTO rooms (
                    semester_id, code, building_code, building_name, label,
                    room_type, capacity
                ) VALUES (
                    '2026-1', 'R101', 'ENG', '공학관', '공학관 101호', 'LECTURE', 40
                );

                INSERT INTO sessions (
                    semester_id, course_code, section_code, day,
                    start_minute, end_minute, room_code
                ) VALUES
                    ('2026-1', 'CSE100', '01', '월', 540, 630, 'R101'),
                    ('2026-1', 'CSE200', '01', '화', 660, 750, NULL);

                INSERT INTO section_academic_units (
                    semester_id, course_code, section_code, academic_unit_code,
                    relation_type, source_kind
                ) VALUES
                    ('2026-1', 'CSE100', '01', 'D1', 'OFFERING', 'CURRICULUM'),
                    ('2026-1', 'CSE200', '01', 'D1', 'OFFERING', 'CURRICULUM');

                INSERT INTO users (id, display_name, primary_email) VALUES
                    ('00000000-0000-0000-0000-000000000001', '리뷰어1', 'reviewer1@example.com'),
                    ('00000000-0000-0000-0000-000000000002', '리뷰어2', 'reviewer2@example.com'),
                    ('00000000-0000-0000-0000-000000000003', '리뷰어3', 'reviewer3@example.com');

                INSERT INTO course_reviews (
                    user_id, course_code, course_name, professor,
                    semester, rating, content
                ) VALUES
                    (
                        '00000000-0000-0000-0000-000000000001',
                        'CSE100', '자료구조', '홍길동', '2026-1', 5, '좋아요'
                    ),
                    (
                        '00000000-0000-0000-0000-000000000002',
                        'CSE100', '자료구조', '홍길동', '2026-1', 4, '유익해요'
                    ),
                    (
                        '00000000-0000-0000-0000-000000000003',
                        'CSE200', '알고리즘', '김교수', '2026-1', 5, '추천해요'
                    );
                """);
    }

    private RequestPostProcessor signedInAs(String userId) {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.fromString(userId), "20260001");
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of()));
    }
}
