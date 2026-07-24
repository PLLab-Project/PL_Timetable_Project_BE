package com.example.pl_timetable_project.auth.security;

import com.example.pl_timetable_project.auth.config.AuthProperties;
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/otp/**",
                                "/api/v1/health/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/error").permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/departments/**",
                                "/api/v1/semesters/**",
                                "/api/v1/courses/**",
                                "/api/v1/graduation/rules").permitAll()
                        .anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(csrf -> csrf
                        .spa()
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/v1/auth/otp/**"))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, error) ->
                                writeError(response, objectMapper, 401, "AUTH_SESSION_EXPIRED", "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, error) ->
                                writeError(response, objectMapper, 403, "COMMON_FORBIDDEN", "접근 권한이 없습니다.")))
                .build();
    }

    /** 허용된 프론트엔드 주소만 세션 쿠키를 포함한 API 요청을 보낼 수 있습니다. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper,
                                   int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), new ApiResponse<>(code, message, null));
    }
}
