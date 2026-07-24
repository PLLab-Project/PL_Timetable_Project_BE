package com.example.pl_timetable_project.timetable;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pl_timetable_project.timetable.dto.request.TimetableCourseRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCreateRequest;
import com.example.pl_timetable_project.timetable.dto.response.TimetableResponse;
import com.example.pl_timetable_project.timetable.service.TimetableService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
class TimetableSectionIdIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TimetableService timetableService;

    private UUID userId;

    @BeforeEach
    void setUpCatalog() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("insert into users (id) values (?)", userId);
        jdbcTemplate.update("""
                insert into semesters
                    (id, prepared_at, dataset_version, source_checksum, is_active, created_at)
                values ('2099-1', current_date, 'test', 'test', true, now())
                """);
        jdbcTemplate.update("""
                insert into courses (semester_id, course_code, name, category, credits)
                values
                    ('2099-1', 'A', 'course A', 'major', 3),
                    ('2099-1', 'B', 'course B', 'major', 3)
                """);
        jdbcTemplate.update("""
                insert into sections (
                    semester_id, course_code, section_code, professor,
                    raw_lecture_time, time_to_be_announced, warning_codes)
                values
                    ('2099-1', 'A', '01', 'professor', '', false, '[]'),
                    ('2099-1', 'A', '02', 'professor', '', false, '[]'),
                    ('2099-1', 'B', '01', 'professor', '', false, '[]')
                """);
    }

    @Test
    void returnsPersistedSectionIdsAfterReplacingAndAddingSections() {
        TimetableResponse created = timetableService.createTimetable(
                userId,
                new TimetableCreateRequest(
                        "test",
                        "2099-1",
                        List.of(new TimetableCourseRequest("A", "01"))));

        TimetableResponse replaced = timetableService.updateSections(
                userId,
                created.id(),
                List.of(new TimetableCourseRequest("A", "02")));
        TimetableResponse added = timetableService.addCourse(
                userId,
                created.id(),
                new TimetableCourseRequest("B", "01"));

        assertThat(replaced.sections()).allSatisfy(section -> assertThat(section.id()).isNotNull());
        assertThat(added.sections()).allSatisfy(section -> assertThat(section.id()).isNotNull());
    }
}
