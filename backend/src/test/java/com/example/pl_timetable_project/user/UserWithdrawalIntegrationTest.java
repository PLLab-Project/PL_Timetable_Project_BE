package com.example.pl_timetable_project.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.user.dto.UserDeleteResponse;
import com.example.pl_timetable_project.user.dto.UserUpdateRequest;
import com.example.pl_timetable_project.user.service.UserService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Transactional
class UserWithdrawalIntegrationTest {

    private static final UUID USER_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String STUDENT_NUMBER = "2026000001";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (id, display_name, primary_email)
                VALUES (?, '탈퇴 테스트 사용자', '2026000001@example.ac.kr')
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO student_profiles (user_id, student_number)
                VALUES (?, ?)
                """, USER_ID, STUDENT_NUMBER);
        jdbcTemplate.update("""
                INSERT INTO privacy_consents (user_id, consent_version, agreed)
                VALUES (?, '2026-01', true)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO social_identities (
                    user_id, provider, provider_subject, email
                ) VALUES (?, 'TEST', 'withdrawal-user', '2026000001@example.ac.kr')
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO login_otp_challenges (
                    student_number, email, code_hash, expires_at, resend_available_at
                ) VALUES (?, '2026000001@example.ac.kr', 'bcrypt-test-hash',
                          now() + interval '5 minutes', now() + interval '1 minute')
                """, STUDENT_NUMBER);
        jdbcTemplate.update("""
                INSERT INTO course_reviews (
                    user_id, course_code, course_name, professor, semester, rating, content
                ) VALUES (?, 'CSE100', '자료구조', '홍교수', '2026-1', 5, '좋아요')
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO completed_courses (
                    user_id, course_code, course_name, credits, category, semester
                ) VALUES (?, 'CSE100', '자료구조', 3.00, '전공필수', '2026-1')
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO semesters (
                    id, prepared_at, dataset_version, source_checksum, is_active, created_at
                ) VALUES (
                    'withdrawal-test', current_date, 'withdrawal-test-v1',
                    repeat('a', 64), true, now()
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO timetables (user_id, semester_id, name)
                VALUES (?, 'withdrawal-test', '삭제할 시간표')
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO optimization_jobs (
                    user_id, timetable_id, semester_id, status,
                    min_credits, max_credits, target_credits,
                    available_start_minute, available_end_minute,
                    lunch_start_minute, lunch_end_minute,
                    max_daily_class_minutes
                )
                SELECT ?, id, semester_id, 'PENDING',
                       12, 21, 18, 540, 1080, 720, 780, 480
                FROM timetables
                WHERE user_id = ?
                """, USER_ID, USER_ID);
    }

    @Test
    void deletesAccountAndEveryUserOwnedRecord() {
        UserDeleteResponse response = userService.withdraw(USER_ID, true);

        assertThat(response.message()).contains("모두 삭제");
        assertCount("users", "id", USER_ID, 0);
        assertCount("student_profiles", "user_id", USER_ID, 0);
        assertCount("privacy_consents", "user_id", USER_ID, 0);
        assertCount("social_identities", "user_id", USER_ID, 0);
        assertCount("course_reviews", "user_id", USER_ID, 0);
        assertCount("completed_courses", "user_id", USER_ID, 0);
        assertCount("timetables", "user_id", USER_ID, 0);
        assertCount("optimization_jobs", "user_id", USER_ID, 0);

        Integer otpCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM login_otp_challenges WHERE student_number = ?",
                Integer.class,
                STUDENT_NUMBER
        );
        assertThat(otpCount).isZero();
    }

    @Test
    void updatesGraduationProfileAndTutorialStateWithoutChangingVerifiedStudentNumber() {
        jdbcTemplate.execute("""
                INSERT INTO academic_colleges (
                    code, name, first_seen_year, last_seen_year, is_current
                ) VALUES ('C1', '공과대학', 2020, 2026, true);

                INSERT INTO academic_units (
                    code, college_code, name, code_source,
                    first_seen_year, last_seen_year, is_current
                ) VALUES (
                    'D1', 'C1', '컴퓨터공학과', 'OFFICIAL_CURRICULUM',
                    2020, 2026, true
                );
                """);

        var response = userService.update(
                USER_ID,
                new UserUpdateRequest(
                        STUDENT_NUMBER,
                        "수정 사용자",
                        (short) 3,
                        "D1",
                        2022,
                        "domestic",
                        "ADVANCED_MAJOR",
                        true));

        assertThat(response.studentNumber()).isEqualTo(STUDENT_NUMBER);
        assertThat(response.admissionYear()).isEqualTo(2022);
        assertThat(response.studentType()).isEqualTo("DOMESTIC");
        assertThat(response.programPath()).isEqualTo("ADVANCED_MAJOR");
        assertThat(response.profileCompleted()).isTrue();
        assertThat(response.graduationProfileCompleted()).isTrue();
        assertThat(response.tutorialCompleted()).isTrue();
    }

    @Test
    void rejectsStudentNumberChangeWithoutSchoolOtpVerification() {
        assertThatThrownBy(() -> userService.update(
                USER_ID,
                new UserUpdateRequest(
                        "2026999999",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(UserErrorCode.STUDENT_NUMBER_VERIFICATION_REQUIRED));
    }

    private void assertCount(String table, String column, UUID value, int expected) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                value
        );
        assertThat(count).isEqualTo(expected);
    }
}
