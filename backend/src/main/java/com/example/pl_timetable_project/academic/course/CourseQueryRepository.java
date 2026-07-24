package com.example.pl_timetable_project.academic.course;

import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.course.dto.CourseAcademicUnitResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseDetailResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseSummaryResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseQueryRepository {

    private static final String REVIEW_CTE = """
            WITH review_stats AS (
                SELECT course_code,
                       avg(rating)::numeric(4,2) AS rating_average,
                       count(*) AS review_count
                  FROM course_reviews
                 WHERE semester = :semesterId
                 GROUP BY course_code
            ),
            global_review_stats AS (
                SELECT avg(rating)::numeric(4,2) AS global_average
                  FROM course_reviews
                 WHERE semester = :semesterId
            )
            """;

    private static final String SEARCH_FILTER = """
             WHERE c.semester_id = :semesterId
               AND (
                    CAST(:query AS text) IS NULL
                    OR lower(c.course_code) LIKE '%'
                        || lower(CAST(:query AS text)) || '%'
                    OR lower(c.name) LIKE '%'
                        || lower(CAST(:query AS text)) || '%'
                    OR EXISTS (
                        SELECT 1
                          FROM sections query_section
                         WHERE query_section.semester_id = c.semester_id
                           AND query_section.course_code = c.course_code
                           AND lower(coalesce(query_section.professor, ''))
                               LIKE '%' || lower(CAST(:query AS text)) || '%'
                    )
               )
               AND (
                    CAST(:category AS text) IS NULL
                    OR c.category = CAST(:category AS text)
               )
               AND (
                    CAST(:credits AS numeric) IS NULL
                    OR c.credits = CAST(:credits AS numeric)
               )
               AND (
                    CAST(:academicUnitCode AS text) IS NULL
                    OR EXISTS (
                        SELECT 1
                          FROM section_academic_units sau
                         WHERE sau.semester_id = c.semester_id
                           AND sau.course_code = c.course_code
                           AND sau.academic_unit_code =
                               CAST(:academicUnitCode AS text)
                    )
               )
               AND (
                    CAST(:professor AS text) IS NULL
                    OR EXISTS (
                        SELECT 1
                          FROM sections professor_section
                         WHERE professor_section.semester_id = c.semester_id
                           AND professor_section.course_code = c.course_code
                           AND lower(coalesce(professor_section.professor, ''))
                               LIKE '%' || lower(CAST(:professor AS text)) || '%'
                    )
               )
               AND (
                    CAST(:dayCode AS text) IS NULL
                    OR EXISTS (
                        SELECT 1
                          FROM sessions day_session
                         WHERE day_session.semester_id = c.semester_id
                           AND day_session.course_code = c.course_code
                           AND day_session.day = CAST(:dayCode AS text)
                    )
               )
            """;

    private static final String BAYESIAN_RATING = """
            CASE
                WHEN coalesce(rs.review_count, 0) = 0 THEN NULL
                ELSE round(
                    (
                        rs.review_count * rs.rating_average
                        + 5 * coalesce(grs.global_average, rs.rating_average)
                    ) / (rs.review_count + 5),
                    2
                )
            END
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CourseQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean semesterExists(String semesterId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM semesters WHERE id = :semesterId)
                """, Map.of("semesterId", semesterId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public List<CourseSummaryResponse> findCourses(
            CourseSearchCondition condition,
            CourseSort sort,
            PageSpec pageSpec) {
        MapSqlParameterSource parameters = parameters(condition)
                .addValue("limit", pageSpec.size())
                .addValue("offset", pageSpec.offset());
        return jdbcTemplate.query(REVIEW_CTE + """
                SELECT c.semester_id, c.course_code, c.name, c.category, c.credits,
                       (
                           SELECT count(*)
                             FROM sections section_count
                            WHERE section_count.semester_id = c.semester_id
                              AND section_count.course_code = c.course_code
                       ) AS section_count,
                       rs.rating_average,
                       coalesce(rs.review_count, 0) AS review_count,
                """ + BAYESIAN_RATING + " AS bayesian_rating"
                + """
                  FROM courses c
                  LEFT JOIN review_stats rs ON rs.course_code = c.course_code
                  CROSS JOIN global_review_stats grs
                """ + SEARCH_FILTER
                + " ORDER BY " + orderBy(sort)
                + " LIMIT :limit OFFSET :offset",
                parameters,
                (rs, rowNum) -> new CourseSummaryResponse(
                        rs.getString("semester_id"),
                        rs.getString("course_code"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getBigDecimal("credits"),
                        rs.getInt("section_count"),
                        rs.getBigDecimal("rating_average"),
                        rs.getLong("review_count"),
                        rs.getBigDecimal("bayesian_rating")));
    }

    public long countCourses(CourseSearchCondition condition) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM courses c " + SEARCH_FILTER,
                parameters(condition),
                Long.class);
        return count == null ? 0 : count;
    }

    public Optional<CourseDetailResponse> findCourse(
            String semesterId, String courseCode) {
        Map<String, ?> parameters =
                Map.of("semesterId", semesterId, "courseCode", courseCode);
        Optional<CourseRow> course = jdbcTemplate.query(REVIEW_CTE + """
                SELECT c.semester_id, c.course_code, c.name, c.category, c.credits,
                       (
                           SELECT count(*)
                             FROM sections section_count
                            WHERE section_count.semester_id = c.semester_id
                              AND section_count.course_code = c.course_code
                       ) AS section_count,
                       rs.rating_average,
                       coalesce(rs.review_count, 0) AS review_count,
                """ + BAYESIAN_RATING + " AS bayesian_rating"
                + """
                  FROM courses c
                  LEFT JOIN review_stats rs ON rs.course_code = c.course_code
                  CROSS JOIN global_review_stats grs
                 WHERE c.semester_id = :semesterId
                   AND c.course_code = :courseCode
                """, parameters, (rs, rowNum) -> new CourseRow(
                rs.getString("semester_id"),
                rs.getString("course_code"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getBigDecimal("credits"),
                rs.getInt("section_count"),
                rs.getBigDecimal("rating_average"),
                rs.getLong("review_count"),
                rs.getBigDecimal("bayesian_rating")))
                .stream()
                .findFirst();
        return course.map(row -> new CourseDetailResponse(
                row.semesterId(),
                row.courseCode(),
                row.name(),
                row.category(),
                row.credits(),
                row.sectionCount(),
                row.ratingAverage(),
                row.reviewCount(),
                row.bayesianRating(),
                findCourseAcademicUnits(semesterId, courseCode)));
    }

    private List<CourseAcademicUnitResponse> findCourseAcademicUnits(
            String semesterId, String courseCode) {
        return jdbcTemplate.query("""
                SELECT DISTINCT u.code, u.name, sau.relation_type
                  FROM section_academic_units sau
                  JOIN academic_units u ON u.code = sau.academic_unit_code
                 WHERE sau.semester_id = :semesterId
                   AND sau.course_code = :courseCode
                 ORDER BY u.name, u.code, sau.relation_type
                """, Map.of("semesterId", semesterId, "courseCode", courseCode),
                (rs, rowNum) -> new CourseAcademicUnitResponse(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("relation_type")));
    }

    private MapSqlParameterSource parameters(CourseSearchCondition condition) {
        return new MapSqlParameterSource()
                .addValue("semesterId", condition.semesterId())
                .addValue("query", condition.query())
                .addValue("category", condition.category())
                .addValue("academicUnitCode", condition.academicUnitCode())
                .addValue("professor", condition.professor())
                .addValue("credits", condition.credits())
                .addValue("dayCode", condition.dayCode());
    }

    private String orderBy(CourseSort sort) {
        return switch (sort) {
            case NAME_ASC -> "c.name ASC, c.course_code ASC";
            case RATING_DESC ->
                    "bayesian_rating DESC NULLS LAST, rs.review_count DESC NULLS LAST, "
                            + "c.name ASC, c.course_code ASC";
            case POPULARITY_DESC ->
                    "coalesce(rs.review_count, 0) DESC, bayesian_rating DESC NULLS LAST, "
                            + "c.name ASC, c.course_code ASC";
        };
    }

    private record CourseRow(
            String semesterId,
            String courseCode,
            String name,
            String category,
            BigDecimal credits,
            int sectionCount,
            BigDecimal ratingAverage,
            long reviewCount,
            BigDecimal bayesianRating) {
    }

}
