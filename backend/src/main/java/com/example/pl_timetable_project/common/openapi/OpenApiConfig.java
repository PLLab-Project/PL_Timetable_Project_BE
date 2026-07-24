package com.example.pl_timetable_project.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SESSION_COOKIE = "sessionCookie";
    private static final String CSRF_HEADER = "csrfHeader";
    private static final String JSON = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
    private static final String ERROR_SCHEMA = "#/components/schemas/ApiErrorResponse";

    @Bean
    OpenAPI plTimetableOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PL Timetable API")
                        .version("v1")
                        .description("""
                                대진대학교 시간표·학사·졸업요건 백엔드 API입니다.

                                로그인 성공 후 브라우저가 `JSESSIONID` 쿠키를 자동 전송해야 합니다.
                                POST·PATCH·DELETE 요청은 `XSRF-TOKEN` 쿠키 값을
                                `X-XSRF-TOKEN` 헤더에도 포함해야 합니다.
                                """)
                .contact(new Contact().name("PL Lab")))
                .components(new Components()
                        .addSchemas("ApiErrorResponse", apiErrorSchema())
                        .addSecuritySchemes(SESSION_COOKIE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("OTP 로그인 후 발급되는 서버 세션 쿠키"))
                        .addSecuritySchemes(CSRF_HEADER, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("XSRF-TOKEN 쿠키의 값을 그대로 전송")));
    }

    private ObjectSchema apiErrorSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("모든 API가 공통으로 사용하는 오류 응답");
        schema.addProperty("code", new StringSchema().example("VALIDATION_ERROR"));
        schema.addProperty(
                "message",
                new StringSchema().example("요청값이 올바르지 않습니다."));
        schema.addProperty("data", new ObjectSchema().nullable(true));
        schema.setRequired(List.of("code", "message"));
        return schema;
    }

    @Bean
    OpenApiCustomizer apiContractCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    operation.setTags(List.of(tagFor(path)));
                    applySecurity(path, method, operation);
                    normalizeSuccessMediaTypes(operation);
                    documentCommonErrors(path, method, operation);
                }));
    }

    private void normalizeSuccessMediaTypes(Operation operation) {
        operation.getResponses().values().forEach(response -> {
            Content content = response.getContent();
            if (content != null && content.containsKey("*/*")) {
                MediaType schema = content.remove("*/*");
                content.addMediaType(JSON, schema);
            }
        });
    }

    private void documentCommonErrors(
            String path, HttpMethod method, Operation operation) {
        operation.getResponses().putIfAbsent(
                "400", errorResponse("요청값 또는 조회 조건이 올바르지 않음"));
        operation.getResponses().putIfAbsent(
                "500", errorResponse("예상하지 못한 서버 오류"));
        if (!isPublic(path, method)) {
            operation.getResponses().putIfAbsent(
                    "401", errorResponse("로그인 세션이 없거나 만료됨"));
            operation.getResponses().putIfAbsent(
                    "403", errorResponse("접근 권한 또는 CSRF 검증 실패"));
        }
    }

    private ApiResponse errorResponse(String description) {
        Schema<?> schema = new ObjectSchema().$ref(ERROR_SCHEMA);
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        JSON, new MediaType().schema(schema)));
    }

    private void applySecurity(String path, HttpMethod method, Operation operation) {
        if (isPublic(path, method)) {
            return;
        }
        SecurityRequirement requirement = new SecurityRequirement()
                .addList(SESSION_COOKIE);
        if (requiresCsrf(path, method)) {
            requirement.addList(CSRF_HEADER);
        }
        operation.setSecurity(List.of(requirement));
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (path.startsWith("/api/v1/auth/otp/")
                || path.startsWith("/api/v1/health/")) {
            return true;
        }
        if (method != HttpMethod.GET) {
            return false;
        }
        return path.startsWith("/api/v1/departments")
                || path.startsWith("/api/v1/semesters")
                || path.startsWith("/api/v1/courses")
                || path.equals("/api/v1/graduation/rules");
    }

    private boolean requiresCsrf(String path, HttpMethod method) {
        return method != HttpMethod.GET
                && method != HttpMethod.HEAD
                && method != HttpMethod.OPTIONS
                && !path.startsWith("/api/v1/auth/otp/");
    }

    private String tagFor(String path) {
        if (path.startsWith("/api/v1/auth")) {
            return "인증";
        }
        if (path.startsWith("/api/v1/users")) {
            return "사용자";
        }
        if (path.startsWith("/api/v1/departments")) {
            return "학과";
        }
        if (path.startsWith("/api/v1/semesters")) {
            return "학기";
        }
        if (path.startsWith("/api/v1/courses/reviews")
                || path.startsWith("/api/v1/reviews")) {
            return "리뷰";
        }
        if (path.startsWith("/api/v1/courses")) {
            return "강의";
        }
        if (path.startsWith("/api/v1/completed-courses")) {
            return "이수과목";
        }
        if (path.startsWith("/api/v1/graduation")) {
            return "졸업요건";
        }
        if (path.startsWith("/api/v1/timetables")) {
            return "시간표";
        }
        if (path.startsWith("/api/v1/optimizations")) {
            return "자동편성";
        }
        return "시스템";
    }
}
