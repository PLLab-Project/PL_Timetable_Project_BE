package com.example.pl_timetable_project.optimization;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pl_timetable_project.optimization.dto.response.OptimizationJobResponse;
import com.example.pl_timetable_project.optimization.service.OptimizationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class OptimizationJobReadIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OptimizationService optimizationService;

    @Test
    void readsPersistedResultsAndTheirLazyCourseSlots() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("insert into users (id) values (?)", userId);
        jdbcTemplate.update("""
                insert into semesters
                    (id, prepared_at, dataset_version, source_checksum, is_active, created_at)
                values ('2099-1', current_date, 'test', 'test', true, now())
                """);
        Long timetableId = jdbcTemplate.queryForObject("""
                insert into timetables (user_id, semester_id, name)
                values (?, '2099-1', 'test')
                returning id
                """, Long.class, userId);
        Long jobId = jdbcTemplate.queryForObject("""
                insert into optimization_jobs (
                    user_id, timetable_id, semester_id, status,
                    min_credits, max_credits, target_credits,
                    available_start_minute, available_end_minute,
                    lunch_start_minute, lunch_end_minute,
                    max_daily_class_minutes)
                values (?, ?, '2099-1', 'SUCCESS',
                    0, 3, 3, 480, 1200, 720, 780, 600)
                returning id
                """, Long.class, userId, timetableId);
        jdbcTemplate.update("""
                insert into optimization_results (
                    job_id, rank, attendance_days, total_credits,
                    total_free_minutes, score)
                values (?, 1, 1, 3, 0, 100)
                """, jobId);

        OptimizationJobResponse response = optimizationService.getJob(userId, jobId);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).sections()).isEmpty();
    }
}
