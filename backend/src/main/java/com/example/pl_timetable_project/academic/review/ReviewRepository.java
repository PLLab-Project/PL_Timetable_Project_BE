package com.example.pl_timetable_project.academic.review;

import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.review.dto.ReviewResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewRepository {

    private static final String PUBLIC_FILTER = """
             WHERE (
                    CAST(:semesterId AS text) IS NULL
                    OR semester = CAST(:semesterId AS text)
               )
               AND (
                    CAST(:courseCode AS text) IS NULL
                    OR course_code = CAST(:courseCode AS text)
               )
               AND (
                    CAST(:professor AS text) IS NULL
                    OR lower(coalesce(professor, '')) =
                       lower(CAST(:professor AS text))
               )
            """;

    private static final String REVIEW_COLUMNS = """
            id, course_code, course_name, professor, semester,
            rating, content, created_at, updated_at
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReviewRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReviewResponse> findPublic(
            String semesterId,
            String courseCode,
            String professor,
            PageSpec pageSpec) {
        MapSqlParameterSource parameters = searchParameters(
                semesterId, courseCode, professor)
                .addValue("limit", pageSpec.size())
                .addValue("offset", pageSpec.offset());
        return jdbcTemplate.query("""
                SELECT
                """ + REVIEW_COLUMNS + """
                  FROM course_reviews
                """ + PUBLIC_FILTER + """
                 ORDER BY created_at DESC, id DESC
                 LIMIT :limit OFFSET :offset
                """, parameters, ReviewRepository::mapReview);
    }

    public long countPublic(
            String semesterId, String courseCode, String professor) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM course_reviews
                """ + PUBLIC_FILTER,
                searchParameters(semesterId, courseCode, professor),
                Long.class);
        return count == null ? 0 : count;
    }

    public List<ReviewResponse> findMine(
            UUID userId, String semesterId, PageSpec pageSpec) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("semesterId", semesterId)
                .addValue("limit", pageSpec.size())
                .addValue("offset", pageSpec.offset());
        return jdbcTemplate.query("""
                SELECT
                """ + REVIEW_COLUMNS + """
                  FROM course_reviews
                 WHERE user_id = :userId
                   AND (
                        CAST(:semesterId AS text) IS NULL
                        OR semester = CAST(:semesterId AS text)
                   )
                 ORDER BY created_at DESC, id DESC
                 LIMIT :limit OFFSET :offset
                """, parameters, ReviewRepository::mapReview);
    }

    public long countMine(UUID userId, String semesterId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM course_reviews
                 WHERE user_id = :userId
                   AND (
                        CAST(:semesterId AS text) IS NULL
                        OR semester = CAST(:semesterId AS text)
                   )
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("semesterId", semesterId), Long.class);
        return count == null ? 0 : count;
    }

    public Optional<CourseOffering> findCourseOffering(
            String semesterId, String courseCode) {
        return jdbcTemplate.query("""
                SELECT course_code, name
                  FROM courses
                 WHERE semester_id = :semesterId
                   AND course_code = :courseCode
                """, Map.of(
                "semesterId", semesterId,
                "courseCode", courseCode),
                (resultSet, rowNumber) -> new CourseOffering(
                        resultSet.getString("course_code"),
                        resultSet.getString("name")))
                .stream()
                .findFirst();
    }

    public boolean professorExists(
            String semesterId, String courseCode, String professor) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                     FROM sections
                     WHERE semester_id = :semesterId
                       AND course_code = :courseCode
                       AND professor = :professor
                )
                """, Map.of(
                "semesterId", semesterId,
                "courseCode", courseCode,
                "professor", professor), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public boolean duplicateExists(
            UUID userId,
            String semesterId,
            String courseCode,
            String professor) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM course_reviews
                     WHERE user_id = :userId
                       AND semester = :semesterId
                       AND course_code = :courseCode
                       AND professor IS NOT DISTINCT FROM
                           CAST(:professor AS text)
                )
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("semesterId", semesterId)
                .addValue("courseCode", courseCode)
                .addValue("professor", professor), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public ReviewResponse create(
            UUID userId,
            CourseOffering course,
            String professor,
            String semesterId,
            int rating,
            String content) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("courseCode", course.courseCode())
                .addValue("courseName", course.courseName())
                .addValue("professor", professor)
                .addValue("semester", semesterId)
                .addValue("rating", rating)
                .addValue("content", content);
        return requiredSingle(jdbcTemplate.query("""
                INSERT INTO course_reviews (
                    user_id, course_code, course_name, professor,
                    semester, rating, content
                ) VALUES (
                    :userId, :courseCode, :courseName, :professor,
                    :semester, :rating, :content
                )
                RETURNING
                """ + REVIEW_COLUMNS,
                parameters,
                ReviewRepository::mapReview));
    }

    public Optional<ReviewResponse> update(
            UUID reviewId, UUID userId, int rating, String content) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("reviewId", reviewId)
                .addValue("userId", userId)
                .addValue("rating", rating)
                .addValue("content", content);
        return jdbcTemplate.query("""
                UPDATE course_reviews
                   SET rating = :rating,
                       content = :content,
                       updated_at = now()
                 WHERE id = :reviewId
                   AND user_id = :userId
                RETURNING
                """ + REVIEW_COLUMNS,
                parameters,
                ReviewRepository::mapReview)
                .stream()
                .findFirst();
    }

    public boolean delete(UUID reviewId, UUID userId) {
        return jdbcTemplate.update("""
                DELETE FROM course_reviews
                 WHERE id = :reviewId
                   AND user_id = :userId
                """, Map.of(
                "reviewId", reviewId,
                "userId", userId)) == 1;
    }

    private MapSqlParameterSource searchParameters(
            String semesterId, String courseCode, String professor) {
        return new MapSqlParameterSource()
                .addValue("semesterId", semesterId)
                .addValue("courseCode", courseCode)
                .addValue("professor", professor);
    }

    private static ReviewResponse requiredSingle(List<ReviewResponse> reviews) {
        if (reviews.size() != 1) {
            throw new IllegalStateException("리뷰 저장 결과를 읽을 수 없습니다.");
        }
        return reviews.get(0);
    }

    private static ReviewResponse mapReview(
            ResultSet resultSet, int rowNumber) throws SQLException {
        return new ReviewResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("course_code"),
                resultSet.getString("course_name"),
                resultSet.getString("professor"),
                resultSet.getString("semester"),
                resultSet.getInt("rating"),
                resultSet.getString("content"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    public record CourseOffering(String courseCode, String courseName) {
    }
}
