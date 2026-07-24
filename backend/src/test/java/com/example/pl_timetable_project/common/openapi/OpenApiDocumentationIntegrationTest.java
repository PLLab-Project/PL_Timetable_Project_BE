package com.example.pl_timetable_project.common.openapi;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class OpenApiDocumentationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private WebApplicationContext applicationContext;

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
                .andExpect(jsonPath("$.paths['/api/v1/semesters'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/courses'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reviews'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/completed-courses'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/graduation/rules'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/timetables'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/optimizations'].post").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/courses'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value(endsWith("ApiResponseAcademicPageResponseCourseSummaryResponse")))
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
    void documentsSessionAndCsrfRequirementsOnlyWhereNeeded() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/courses'].get.security").doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/auth/session'].get.security[0].sessionCookie").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables'].post.security[0].sessionCookie").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/timetables'].post.security[0].csrfHeader").exists());
    }

    @Test
    void exposesSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
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
