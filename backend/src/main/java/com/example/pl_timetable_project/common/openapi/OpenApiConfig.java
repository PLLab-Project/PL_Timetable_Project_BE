package com.example.pl_timetable_project.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
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

    @Bean
    OpenApiCustomizer securityAndTagCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    operation.setTags(List.of(tagFor(path)));
                    applySecurity(path, method, operation);
                }));
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
