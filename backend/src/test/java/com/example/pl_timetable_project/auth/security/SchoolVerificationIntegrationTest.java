package com.example.pl_timetable_project.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.HttpSession;
import jakarta.persistence.EntityManager;
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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
class SchoolVerificationIntegrationTest {

    private static final UUID USER_ID =
            UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final String STUDENT_NUMBER = "20261234";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private UsernamePasswordAuthenticationToken unverifiedAuthentication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        jdbcTemplate.update("""
                INSERT INTO users (id, display_name, primary_email)
                VALUES (?, 'Google 사용자', 'google-user@gmail.com')
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO student_profiles (user_id)
                VALUES (?)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO social_identities (
                    user_id, provider, provider_subject, email
                ) VALUES (?, 'GOOGLE', 'google-school-verification', 'google-user@gmail.com')
                """, USER_ID);

        unverifiedAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(USER_ID, null, false), null, List.of());
    }

    @Test
    void allowsStudentFeaturesWithoutSchoolOtp() throws Exception {
        mockMvc.perform(get("/api/v1/timetables")
                        .with(authentication(unverifiedAuthentication)))
                .andExpect(status().isOk());
    }

    @Test
    void optionalSchoolVerificationUpdatesSession() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO login_otp_challenges (
                    student_number, email, code_hash,
                    expires_at, resend_available_at
                ) VALUES (?, ?, ?, now() + interval '5 minutes', now())
                """,
                STUDENT_NUMBER,
                STUDENT_NUMBER + "@daejin.ac.kr",
                passwordEncoder.encode("123456"));

        MvcResult verification = mockMvc.perform(
                        post("/api/v1/auth/school-verification/verify")
                                .with(authentication(unverifiedAuthentication))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "studentNumber": "20261234",
                                          "code": "123456"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.studentNumber").value(STUDENT_NUMBER))
                .andReturn();

        HttpSession session = verification.getRequest().getSession(false);
        assertThat(session).isNotNull();
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getPrincipal())
                .isEqualTo(new AuthenticatedUser(USER_ID, STUDENT_NUMBER, true));

        mockMvc.perform(get("/api/v1/timetables").session(
                        (org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/session").session(
                        (org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.schoolVerified").value(true))
                .andExpect(jsonPath("$.data.user.studentNumber").value(STUDENT_NUMBER));

        entityManager.flush();
        Boolean verified = jdbcTemplate.queryForObject("""
                SELECT school_verified_at IS NOT NULL
                  FROM student_profiles
                 WHERE user_id = ?
                """, Boolean.class, USER_ID);
        assertThat(verified).isTrue();
    }

    @Test
    void allowsUnverifiedGoogleUserToRequestSchoolOtp() throws Exception {
        mockMvc.perform(post("/api/v1/auth/school-verification/request")
                        .with(authentication(unverifiedAuthentication))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentNumber": "20261234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cooldownSeconds").value(60));

        entityManager.flush();
        Integer challengeCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM login_otp_challenges
                 WHERE student_number = ?
                   AND consumed_at IS NULL
                """, Integer.class, STUDENT_NUMBER);
        assertThat(challengeCount).isEqualTo(1);
    }

    @Test
    void requiresNewOtpWhenChangingStudentNumberAndDeletesOldChallenges() throws Exception {
        String previousStudentNumber = "20260000";
        String changedStudentNumber = "20269999";
        jdbcTemplate.update("""
                UPDATE student_profiles
                   SET student_number = ?,
                       school_verified_at = now()
                 WHERE user_id = ?
                """, previousStudentNumber, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO login_otp_challenges (
                    student_number, email, code_hash,
                    expires_at, resend_available_at
                ) VALUES (?, ?, ?, now() + interval '5 minutes', now())
                """,
                previousStudentNumber,
                previousStudentNumber + "@daejin.ac.kr",
                passwordEncoder.encode("111111"));
        jdbcTemplate.update("""
                INSERT INTO login_otp_challenges (
                    student_number, email, code_hash,
                    expires_at, resend_available_at
                ) VALUES (?, ?, ?, now() + interval '5 minutes', now())
                """,
                changedStudentNumber,
                changedStudentNumber + "@daejin.ac.kr",
                passwordEncoder.encode("654321"));

        UsernamePasswordAuthenticationToken verifiedAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(USER_ID, previousStudentNumber, true),
                        null,
                        List.of());

        mockMvc.perform(post("/api/v1/auth/school-verification/verify")
                        .with(authentication(verifiedAuthentication))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentNumber": "20269999",
                                  "code": "654321"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.studentNumber").value(changedStudentNumber));

        entityManager.flush();
        String savedStudentNumber = jdbcTemplate.queryForObject("""
                SELECT student_number
                  FROM student_profiles
                 WHERE user_id = ?
                """, String.class, USER_ID);
        Integer oldChallengeCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM login_otp_challenges
                 WHERE student_number = ?
                """, Integer.class, previousStudentNumber);

        assertThat(savedStudentNumber).isEqualTo(changedStudentNumber);
        assertThat(oldChallengeCount).isZero();
    }
}
