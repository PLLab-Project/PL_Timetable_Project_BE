package com.example.pl_timetable_project.common.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class OpenApiDocumentationIntegrationTest {

    private static final List<String> REQUEST_SCHEMAS = List.of(
            "OtpStartRequest",
            "OtpVerifyRequest",
            "AcademicProgramUpdateRequest",
            "UserUpdateRequest",
            "ConsentCreateRequest",
            "UserDeleteRequest",
            "ReviewCreateRequest",
            "ReviewUpdateRequest",
            "CompletedCourseCreateRequest",
            "CompletedCourseUpdateRequest",
            "TimetableCourseRequest",
            "TimetableCreateRequest",
            "TimetableSectionsUpdateRequest",
            "TimetableUpdateRequest",
            "TimetableFavoriteUpdateRequest",
            "CourseCandidateRequest",
            "OptimizationCreateRequest",
            "BlockedTimeRequest",
            "TimeRangeRequest");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private WebApplicationContext applicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void exposesOpenApiForEveryImplementedDomainWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("PL Timetable API"))
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfHeader.in").value("header"))
                .andExpect(jsonPath("$.info.description").value(
                        org.hamcrest.Matchers.containsString("OpenAPI 3")))
                .andExpect(jsonPath(
                        "$.components.schemas.CompletedCourseResponse.properties.sourceSnapshot.additionalProperties")
                        .value(true))
                .andExpect(jsonPath("$.paths['/api/v1/auth/otp/request'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/departments'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/departments/colleges'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/semesters'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/courses'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/sections'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reviews'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/completed-courses'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/completed-courses/ocr'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/graduation/rules'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/timetables'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/optimizations'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/optimizations/{jobId}/results/{rank}/apply'].post")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/courses'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value(endsWith("ApiResponseAcademicPageResponseCourseSummaryResponse")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/sections'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value(endsWith("ApiResponseAcademicPageResponseSectionSearchResponse")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables'].post.responses['201'].content['application/json'].schema['$ref']")
                        .value(endsWith("ApiResponseTimetableResponse")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables'].post.responses['401'].content['application/json'].schema['$ref']")
                        .value(endsWith("ApiErrorResponse")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables/{timetableId}'].delete.responses['200'].content['application/json'].schema['$ref']")
                        .value(endsWith("ApiResponseVoid")));
    }

    @Test
    void includesDescriptionsExamplesAndBusinessErrorResponsesForFrontendIntegration()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/courses'].get.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/courses'].get.parameters[?(@.name == 'semesterId')].description")
                        .isNotEmpty())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/sections'].get.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/sections'].get.parameters[?(@.name == 'targetGrade')].description")
                        .isNotEmpty())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables'].post.requestBody.content['application/json'].example.name")
                        .value("2026-1 전공 시간표"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/auth/otp/request'].post.responses['429']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables/{timetableId}'].get.responses['404']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables'].post.responses['409']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/optimizations'].post.responses['422']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/auth/otp/request'].post.responses['429'].content['application/json'].example.code")
                        .value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/auth/csrf'].get.security")
                        .doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/auth/csrf'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value(endsWith("ApiResponseCsrfTokenResponse")));
    }

    @Test
    void documentsRequestFieldsWithMeaningfulDescriptionsAndExamples() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.OtpVerifyRequest.properties.studentNumber.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.components.schemas.OtpVerifyRequest.properties.studentNumber.example")
                        .value("20201234"))
                .andExpect(jsonPath(
                        "$.components.schemas.UserUpdateRequest.properties.departmentId.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.components.schemas.ReviewCreateRequest.properties.rating.example")
                        .value(5))
                .andExpect(jsonPath(
                        "$.components.schemas.CompletedCourseCreateRequest.properties.status.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.components.schemas.TimetableCreateRequest.properties.sections.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.components.schemas.CourseCandidateRequest.properties.required.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.components.schemas.OptimizationCreateRequest.properties.maxDailyClassMinutes.example")
                        .value(360))
                .andExpect(jsonPath(
                        "$.components.schemas.TimeRangeRequest.properties.startTime.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.components.schemas.ApiResponseTimetableResponse.properties.code.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.components.schemas.AcademicPageResponseCourseSummaryResponse.properties.totalElements.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.components.schemas.TimetableResponse.properties.freeTimes.description",
                        not(emptyOrNullString())))
                .andExpect(jsonPath(
                        "$.components.schemas.OptimizationJobResponse.properties.status.description",
                        not(emptyOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode schemas = objectMapper.readTree(document)
                .path("components")
                .path("schemas");
        for (String schemaName : REQUEST_SCHEMAS) {
            JsonNode properties = schemas.path(schemaName).path("properties");
            assertThat(properties.isObject())
                    .as("%s 요청 스키마가 OpenAPI에 존재해야 함", schemaName)
                    .isTrue();
            properties.properties().forEach(entry -> {
                assertThat(entry.getValue().path("description").asText())
                        .as("%s.%s 필드 설명", schemaName, entry.getKey())
                        .isNotBlank();
                assertThat(entry.getValue().has("example"))
                        .as("%s.%s 필드 예제", schemaName, entry.getKey())
                        .isTrue();
            });
        }
    }

    @Test
    void documentsAcademicProgramReplacementContractForFrontend() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/users/me'].patch.description",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("academicPrograms"),
                                org.hamcrest.Matchers.containsString("전체를 교체"),
                                org.hamcrest.Matchers.containsString("PRIMARY는 정확히 하나"),
                                org.hamcrest.Matchers.containsString("중복할 수 없습니다"))))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/users/me'].patch.requestBody.content['application/json'].example.academicPrograms.length()")
                        .value(2))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/users/me'].patch.requestBody.content['application/json'].example.academicPrograms[0].academicUnitCode")
                        .value("AA0194"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/users/me'].patch.requestBody.content['application/json'].example.academicPrograms[0].role")
                        .value("PRIMARY"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/users/me'].patch.requestBody.content['application/json'].example.academicPrograms[1].role")
                        .value("DOUBLE_MAJOR"))
                .andExpect(jsonPath(
                        "$.components.schemas.UserUpdateRequest.properties.academicPrograms.description",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("전체를 교체"),
                                org.hamcrest.Matchers.containsString("PRIMARY는 정확히 하나"),
                                org.hamcrest.Matchers.containsString("중복 불가"),
                                org.hamcrest.Matchers.containsString("개수는 고정하지 않음"))))
                .andExpect(jsonPath(
                        "$.components.schemas.UserUpdateRequest.properties.programPath.description",
                        org.hamcrest.Matchers.containsString("호환용")))
                .andExpect(jsonPath(
                        "$.components.schemas.AcademicProgramUpdateRequest.properties.role.enum",
                        org.hamcrest.Matchers.hasItems(
                                "PRIMARY", "DOUBLE_MAJOR", "MINOR", "MICRO_MAJOR")))
                .andExpect(jsonPath(
                        "$.components.schemas.UserInfoResponse.properties.academicPrograms.description",
                        org.hamcrest.Matchers.containsString("가변 길이")))
                .andExpect(jsonPath(
                        "$.components.schemas.AcademicProgramResponse.properties.status.enum",
                        org.hamcrest.Matchers.hasItems(
                                "PLANNED", "ACTIVE", "COMPLETED")))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/users/me'].patch.responses['400'].content['application/json'].example.code")
                        .value("INVALID_ACADEMIC_PROGRAMS"));
    }

    @Test
    void documentsSessionAndCsrfRequirementsOnlyWhereNeeded() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/courses'].get.security").doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/sections'].get.security").doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/auth/session'].get.security[0].sessionCookie").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables'].post.security[0].sessionCookie").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables'].post.security[0].csrfHeader").exists());
    }

    @Test
    void doesNotExposeRedundantSwaggerUi() throws Exception {
        UsernamePasswordAuthenticationToken verifiedUser =
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(UUID.randomUUID(), "20261234", true),
                        null,
                        List.of());

        mockMvc.perform(get("/swagger-ui.html").with(authentication(verifiedUser)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/swagger-ui/index.html").with(authentication(verifiedUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void redirectsRootToModernScalarReferenceWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/scalar"));

        mockMvc.perform(get("/scalar"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "PL Timetable API")));

        mockMvc.perform(get("/favicon.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"));
    }

    @Test
    void exposesYamlSpecificationWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "title: PL Timetable API")));
    }
}
