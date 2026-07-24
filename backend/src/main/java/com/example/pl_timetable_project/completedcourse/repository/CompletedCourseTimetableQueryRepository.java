package com.example.pl_timetable_project.completedcourse.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 시간표 패키지 구현에 결합하지 않고 completedcourse 경계에서 가져오기 원본을 읽습니다.
 */
@Repository
public class CompletedCourseTimetableQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public CompletedCourseTimetableQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsOwnedTimetable(UUID userId, Long timetableId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM timetables
                    WHERE id = ? AND user_id = ?
                )
                """,
                Boolean.class,
                timetableId,
                userId);
        return Boolean.TRUE.equals(exists);
    }

    public List<TimetableSectionSnapshot> findSections(UUID userId, Long timetableId) {
        return jdbcTemplate.query(
                """
                SELECT t.id AS timetable_id,
                       tc.id AS timetable_course_id,
                       t.semester_id,
                       tc.course_code,
                       tc.section_code,
                       tc.course_name,
                       tc.professor_name,
                       tc.credits,
                       c.category
                FROM timetables t
                JOIN timetable_courses tc ON tc.timetable_id = t.id
                JOIN courses c
                  ON c.semester_id = tc.semester_id
                 AND c.course_code = tc.course_code
                WHERE t.id = ?
                  AND t.user_id = ?
                ORDER BY tc.id
                """,
                (resultSet, rowNumber) -> new TimetableSectionSnapshot(
                        resultSet.getLong("timetable_id"),
                        resultSet.getLong("timetable_course_id"),
                        resultSet.getString("semester_id"),
                        resultSet.getString("course_code"),
                        resultSet.getString("section_code"),
                        resultSet.getString("course_name"),
                        resultSet.getString("professor_name"),
                        resultSet.getBigDecimal("credits"),
                        resultSet.getString("category")),
                timetableId,
                userId);
    }

    public record TimetableSectionSnapshot(
            Long timetableId,
            Long timetableCourseId,
            String semesterId,
            String courseCode,
            String sectionCode,
            String courseName,
            String professorName,
            BigDecimal credits,
            String category) {
    }
}
