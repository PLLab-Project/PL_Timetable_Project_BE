package com.example.pl_timetable_project.auth.security;

import com.example.pl_timetable_project.auth.AuthErrorCode;
import com.example.pl_timetable_project.auth.config.AuthProperties;
import com.example.pl_timetable_project.common.exception.CommonErrorCode;
import com.example.pl_timetable_project.common.exception.ErrorCode;
import com.example.pl_timetable_project.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/** 세션 인증, 공개 API, CSRF 정책을 한곳에서 관리합니다. */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            @Value("${app.security.csrf-cookie-secure:false}") boolean csrfCookieSecure,
            @Value("${app.security.csrf-cookie-same-site:Lax}") String csrfCookieSameSite)
            throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .secure(csrfCookieSecure)
                .sameSite(csrfCookieSameSite));

        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/otp/**",
                                "/api/v1/auth/csrf",
                                "/api/v1/health/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/scalar",
                                "/scalar/**",
                                "/favicon.svg",
                                "/",
                                "/error").permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/departments/**",
                                "/api/v1/semesters/**",
                                "/api/v1/courses/**",
                                "/api/v1/sections/**",
                                "/api/v1/graduation/rules").permitAll()
                        .anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(csrf -> csrf
                        .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers("/api/v1/auth/otp/**"))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, error) ->
                                writeError(response, objectMapper, AuthErrorCode.SESSION_EXPIRED))
                        .accessDeniedHandler((request, response, error) ->
                                writeError(response, objectMapper, CommonErrorCode.FORBIDDEN)))
                .build();
    }

    /** 허용된 프론트엔드 주소만 세션 쿠키를 포함한 API 요청을 보낼 수 있습니다. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.allowed-origins:}") List<String> allowedOrigins,
            @Value("${app.security.allowed-origin-patterns:}") List<String> allowedOriginPatterns) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(normalizeCorsValues(allowedOrigins));
        configuration.setAllowedOriginPatterns(normalizeCorsValues(allowedOriginPatterns));
        configuration.setAllowedMethods(
                List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 브라우저가 추가하는 헤더 때문에 정상적인 preflight가 차단되지 않게 허용합니다.
        // Origin과 credentials는 위의 제한된 목록 및 패턴으로 계속 검증됩니다.
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static List<String> normalizeCorsValues(List<String> values) {
        return values.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                // Origin 값에는 마지막 슬래시가 없으므로 흔한 환경 변수 입력 실수를 보정합니다.
                .map(value -> value.endsWith("/") ? value.substring(0, value.length() - 1) : value)
                .distinct()
                .toList();
    }

    private static void writeError(
            HttpServletResponse response,
            ObjectMapper mapper,
            ErrorCode errorCode) throws java.io.IOException {
        response.setStatus(errorCode.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), ApiResponse.error(errorCode));
    }
}
