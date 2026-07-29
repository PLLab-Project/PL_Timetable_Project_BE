package com.example.pl_timetable_project.academic.course;

import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.course.dto.CourseRoomResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseSessionResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionClassificationResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionSearchResponse;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SectionSearchQueryRepository {

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
             WHERE section.semester_id = :semesterId
               AND (
                    CAST(:query AS text) IS NULL
                    OR lower(course.course_code) LIKE '%'
                        || lower(CAST(:query AS text)) || '%'
                    OR lower(course.name) LIKE '%'
                        || lower(CAST(:query AS text)) || '%'
                    OR lower(coalesce(section.professor, '')) LIKE '%'
                        || lower(CAST(:query AS text)) || '%'
               )
               AND (
                    CAST(:categoriesEmpty AS boolean) = true
                    OR course.category IN (:categories)
               )
               AND (
                    CAST(:academicUnitCodesEmpty AS boolean) = true
                    OR EXISTS (
                        SELECT 1
                          FROM section_academic_units unit
                         WHERE unit.semester_id = section.semester_id
                           AND unit.course_code = section.course_code
                           AND unit.section_code = section.section_code
                           AND unit.academic_unit_code IN (:academicUnitCodes)
                    )
               )
               AND (
                    CAST(:collegeCodesEmpty AS boolean) = true
                    OR EXISTS (
                        SELECT 1
                          FROM section_academic_units college_section
                          JOIN academic_units college_unit
                            ON college_unit.code =
                               college_section.academic_unit_code
                         WHERE college_section.semester_id = section.semester_id
                           AND college_section.course_code = section.course_code
                           AND college_section.section_code = section.section_code
                           AND college_unit.college_code IN (:collegeCodes)
                    )
               )
               AND (
                    CAST(:completionCategoriesEmpty AS boolean) = true
                    OR EXISTS (
                        SELECT 1
                          FROM section_classification_contexts classification
                         WHERE classification.semester_id = section.semester_id
                           AND classification.course_code = section.course_code
                           AND classification.section_code = section.section_code
                           AND classification.completion_category
                               IN (:completionCategories)
                           AND (
                                CAST(:academicUnitCodesEmpty AS boolean) = true
                                OR classification.academic_unit_code
                                    IN (:academicUnitCodes)
                           )
                    )
               )
               AND (
                    CAST(:targetGradesEmpty AS boolean) = true
                    OR section.target_grade IN (:targetGrades)
               )
               AND (
                    CAST(:professor AS text) IS NULL
                    OR lower(coalesce(section.professor, '')) LIKE '%'
                        || lower(CAST(:professor AS text)) || '%'
               )
               AND (
                    CAST(:credits AS numeric) IS NULL
                    OR course.credits = CAST(:credits AS numeric)
               )
               AND (
                    CAST(:dayCode AS text) IS NULL
                    OR EXISTS (
                        SELECT 1
                          FROM sessions day_session
                         WHERE day_session.semester_id = section.semester_id
                           AND day_session.course_code = section.course_code
                           AND day_session.section_code = section.section_code
                           AND day_session.day = CAST(:dayCode AS text)
                    )
               )
            """;

    private static final String BAYESIAN_RATING = """
            CASE
                WHEN coalesce(review.review_count, 0) = 0 THEN NULL
                ELSE round(
                    (
                        review.review_count * review.rating_average
                        + 5 * coalesce(global_review.global_average, review.rating_average)
                    ) / (review.review_count + 5),
                    2
                )
            END
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SectionSearchQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SectionSearchResponse> findSections(
            SectionSearchCondition condition,
            CourseSort sort,
            PageSpec pageSpec) {
        MapSqlParameterSource parameters = parameters(condition)
                .addValue("limit", pageSpec.size())
                .addValue("offset", pageSpec.offset());
        List<SectionRow> rows = jdbcTemplate.query(REVIEW_CTE + """
                SELECT section.semester_id, section.course_code,
                       course.name AS course_name, section.section_code,
                       section.professor, course.category, course.credits,
                       course.lecture_hours, course.practice_hours,
                       section.raw_lecture_time, section.raw_location,
                       section.time_to_be_announced, section.target_grade,
                       section.capacity, section.notes,
                       jsonb_array_length(section.warning_codes) AS warning_count,
                       review.rating_average,
                       coalesce(review.review_count, 0) AS review_count,
                """ + BAYESIAN_RATING + " AS bayesian_rating"
                + """
                  FROM sections section
                  JOIN courses course
                    ON course.semester_id = section.semester_id
                   AND course.course_code = section.course_code
                  LEFT JOIN review_stats review
                    ON review.course_code = section.course_code
                  CROSS JOIN global_review_stats global_review
                """ + SEARCH_FILTER
                + " ORDER BY " + orderBy(condition, sort)
                + " LIMIT :limit OFFSET :offset",
                parameters,
                (result, rowNumber) -> new SectionRow(
                        result.getString("semester_id"),
                        result.getString("course_code"),
                        result.getString("course_name"),
                        result.getString("section_code"),
                        result.getString("professor"),
                        result.getString("category"),
                        result.getBigDecimal("credits"),
                        result.getBigDecimal("lecture_hours"),
                        result.getBigDecimal("practice_hours"),
                        result.getString("raw_lecture_time"),
                        result.getString("raw_location"),
                        result.getBoolean("time_to_be_announced"),
                        result.getString("target_grade"),
                        result.getObject("capacity", Integer.class),
                        result.getString("notes"),
                        result.getInt("warning_count"),
                        result.getBigDecimal("rating_average"),
                        result.getLong("review_count"),
                        result.getBigDecimal("bayesian_rating")));
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<SectionKey> keys = new LinkedHashSet<>();
        rows.forEach(row -> keys.add(row.key()));
        Map<SectionKey, List<CourseSessionResponse>> sessions =
                findSessions(condition.semesterId(), keys);
        Map<SectionKey, List<SectionClassificationResponse>> classifications =
                findClassifications(condition.semesterId(), keys);

        return rows.stream()
                .map(row -> row.toResponse(
                        sessions.getOrDefault(row.key(), List.of()),
                        classifications.getOrDefault(row.key(), List.of()),
                        condition.preferredAcademicUnitCode(),
                        condition.completionCategories()))
                .toList();
    }

    public long countSections(SectionSearchCondition condition) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                  FROM sections section
                  JOIN courses course
                    ON course.semester_id = section.semester_id
                   AND course.course_code = section.course_code
                """ + SEARCH_FILTER,
                parameters(condition),
                Long.class);
        return count == null ? 0 : count;
    }

    private Map<SectionKey, List<CourseSessionResponse>> findSessions(
            String semesterId, Set<SectionKey> keys) {
        Set<String> courseCodes = courseCodes(keys);
        Map<Long, MutableSession> sessions = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT session.id, session.course_code, session.section_code,
                       session.day, session.start_minute, session.end_minute,
                       session.room_code,
                       primary_room.label AS room_label,
                       primary_room.building_name,
                       linked.position AS linked_position,
                       linked.room_code AS linked_room_code,
                       linked_room.label AS linked_room_label,
                       linked_room.building_name AS linked_building_name
                  FROM sessions session
                  LEFT JOIN rooms primary_room
                    ON primary_room.semester_id = session.semester_id
                   AND primary_room.code = session.room_code
                  LEFT JOIN session_rooms linked
                    ON linked.session_id = session.id
                   AND linked.semester_id = session.semester_id
                  LEFT JOIN rooms linked_room
                    ON linked_room.semester_id = linked.semester_id
                   AND linked_room.code = linked.room_code
                 WHERE session.semester_id = :semesterId
                   AND session.course_code IN (:courseCodes)
                 ORDER BY session.course_code, session.section_code,
                          session.sequence_no, session.id, linked.position
                """, Map.of(
                        "semesterId", semesterId,
                        "courseCodes", courseCodes),
                result -> {
                    SectionKey key = new SectionKey(
                            result.getString("course_code"),
                            result.getString("section_code"));
                    if (!keys.contains(key)) {
                        return;
                    }
                    long sessionId = result.getLong("id");
                    String day = result.getString("day");
                    int startMinute = result.getInt("start_minute");
                    int endMinute = result.getInt("end_minute");
                    String roomCode = result.getString("room_code");
                    String roomLabel = result.getString("room_label");
                    String buildingName = result.getString("building_name");
                    MutableSession session = sessions.computeIfAbsent(
                            sessionId,
                            ignored -> new MutableSession(
                                    key,
                                    day,
                                    startMinute,
                                    endMinute,
                                    roomCode,
                                    roomLabel,
                                    buildingName));
                    Integer position =
                            result.getObject("linked_position", Integer.class);
                    if (position != null) {
                        session.rooms.add(new CourseRoomResponse(
                                position,
                                result.getString("linked_room_code"),
                                result.getString("linked_room_label"),
                                result.getString("linked_building_name")));
                    }
                });

        Map<SectionKey, List<CourseSessionResponse>> bySection =
                new LinkedHashMap<>();
        sessions.values().forEach(session -> bySection
                .computeIfAbsent(session.key, ignored -> new ArrayList<>())
                .add(session.toResponse()));
        bySection.replaceAll((ignored, values) -> List.copyOf(values));
        return Map.copyOf(bySection);
    }

    private Map<SectionKey, List<SectionClassificationResponse>> findClassifications(
            String semesterId, Set<SectionKey> keys) {
        Map<SectionKey, List<SectionClassificationResponse>> bySection =
                new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT course_code, section_code, context_label, context_kind,
                       academic_unit_code, completion_category, target_grade,
                       is_primary, is_shaded, source_page
                  FROM section_classification_contexts
                 WHERE semester_id = :semesterId
                   AND course_code IN (:courseCodes)
                 ORDER BY course_code, section_code, is_primary DESC,
                          source_page, source_row
                """, Map.of(
                        "semesterId", semesterId,
                        "courseCodes", courseCodes(keys)),
                result -> {
                    SectionKey key = new SectionKey(
                            result.getString("course_code"),
                            result.getString("section_code"));
                    if (!keys.contains(key)) {
                        return;
                    }
                    bySection.computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add(new SectionClassificationResponse(
                                    result.getString("context_label"),
                                    result.getString("context_kind"),
                                    result.getString("academic_unit_code"),
                                    result.getString("completion_category"),
                                    result.getString("target_grade"),
                                    result.getBoolean("is_primary"),
                                    result.getBoolean("is_shaded"),
                                    result.getInt("source_page")));
                });
        bySection.replaceAll((ignored, values) -> List.copyOf(values));
        return Map.copyOf(bySection);
    }

    private Set<String> courseCodes(Set<SectionKey> keys) {
        Set<String> values = new LinkedHashSet<>();
        keys.forEach(key -> values.add(key.courseCode()));
        return values;
    }

    private MapSqlParameterSource parameters(SectionSearchCondition condition) {
        return new MapSqlParameterSource()
                .addValue("semesterId", condition.semesterId())
                .addValue("query", condition.query())
                .addValue("categoriesEmpty", condition.categories().isEmpty())
                .addValue("categories", placeholder(condition.categories()))
                .addValue("academicUnitCodesEmpty", condition.academicUnitCodes().isEmpty())
                .addValue("academicUnitCodes", placeholder(condition.academicUnitCodes()))
                .addValue("collegeCodesEmpty", condition.collegeCodes().isEmpty())
                .addValue("collegeCodes", placeholder(condition.collegeCodes()))
                .addValue(
                        "completionCategoriesEmpty",
                        condition.completionCategories().isEmpty())
                .addValue(
                        "completionCategories",
                        placeholder(condition.completionCategories()))
                .addValue("targetGradesEmpty", condition.targetGrades().isEmpty())
                .addValue("targetGrades", placeholder(condition.targetGrades()))
                .addValue(
                        "preferredAcademicUnitCode",
                        condition.preferredAcademicUnitCode())
                .addValue("professor", condition.professor())
                .addValue("credits", condition.credits())
                .addValue("dayCode", condition.dayCode());
    }

    private String orderBy(SectionSearchCondition condition, CourseSort sort) {
        String preference = condition.preferredAcademicUnitCode() != null
                        && !condition.completionCategories().isEmpty()
                ? """
                  CASE WHEN EXISTS (
                      SELECT 1
                        FROM section_classification_contexts preferred
                       WHERE preferred.semester_id = section.semester_id
                         AND preferred.course_code = section.course_code
                         AND preferred.section_code = section.section_code
                         AND preferred.academic_unit_code =
                             :preferredAcademicUnitCode
                         AND preferred.completion_category
                             IN (:completionCategories)
                  ) THEN 0 ELSE 1 END,
                  """
                : "";
        return preference + switch (sort) {
            case DEFAULT ->
                    "section.source_page ASC NULLS LAST, "
                            + "section.source_row ASC NULLS LAST, "
                            + "course.course_code ASC, section.section_code ASC";
            case NAME_ASC ->
                    "course.name ASC, course.course_code ASC, section.section_code ASC";
            case NAME_DESC ->
                    "course.name DESC, course.course_code DESC, section.section_code DESC";
            case REVIEW_COUNT_DESC ->
                    "coalesce(review.review_count, 0) DESC, course.name ASC, "
                            + "course.course_code ASC, section.section_code ASC";
            case RATING_DESC ->
                    "bayesian_rating DESC NULLS LAST, review.review_count DESC NULLS LAST, "
                            + "course.name ASC, course.course_code ASC, section.section_code ASC";
            case POPULARITY_DESC ->
                    "coalesce(review.review_count, 0) DESC, "
                            + "bayesian_rating DESC NULLS LAST, course.name ASC, "
                            + "course.course_code ASC, section.section_code ASC";
        };
    }

    private List<String> placeholder(List<String> values) {
        return values.isEmpty() ? List.of("") : values;
    }

    private static DayOfWeek toDayOfWeek(String day) {
        return switch (day) {
            case "월" -> DayOfWeek.MONDAY;
            case "화" -> DayOfWeek.TUESDAY;
            case "수" -> DayOfWeek.WEDNESDAY;
            case "목" -> DayOfWeek.THURSDAY;
            case "금" -> DayOfWeek.FRIDAY;
            case "토" -> DayOfWeek.SATURDAY;
            case "일" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalStateException(
                    "지원하지 않는 DB 요일 코드입니다: " + day);
        };
    }

    private record SectionKey(String courseCode, String sectionCode) {
    }

    private record SectionRow(
            String semesterId,
            String courseCode,
            String courseName,
            String sectionCode,
            String professor,
            String category,
            BigDecimal credits,
            BigDecimal lectureHours,
            BigDecimal practiceHours,
            String rawLectureTime,
            String rawLocation,
            boolean timeToBeAnnounced,
            String targetGrade,
            Integer capacity,
            String notes,
            int warningCount,
            BigDecimal ratingAverage,
            long reviewCount,
            BigDecimal bayesianRating) {

        private SectionKey key() {
            return new SectionKey(courseCode, sectionCode);
        }

        private SectionSearchResponse toResponse(
                List<CourseSessionResponse> sessions,
                List<SectionClassificationResponse> classifications,
                String preferredAcademicUnitCode,
                List<String> requestedCompletionCategories) {
            return new SectionSearchResponse(
                    semesterId,
                    courseCode,
                    courseName,
                    sectionCode,
                    professor,
                    category,
                    credits,
                    lectureHours,
                    practiceHours,
                    rawLectureTime,
                    rawLocation,
                    timeToBeAnnounced,
                    targetGrade,
                    selectCompletionCategory(
                            classifications,
                            preferredAcademicUnitCode,
                            requestedCompletionCategories),
                    capacity,
                    notes,
                    warningCount,
                    ratingAverage,
                    reviewCount,
                    bayesianRating,
                    sessions,
                    classifications);
        }

        private String selectCompletionCategory(
                List<SectionClassificationResponse> classifications,
                String preferredAcademicUnitCode,
                List<String> requestedCompletionCategories) {
            if (preferredAcademicUnitCode != null) {
                for (SectionClassificationResponse classification : classifications) {
                    if (preferredAcademicUnitCode.equals(
                                    classification.academicUnitCode())
                            && (requestedCompletionCategories.isEmpty()
                            || requestedCompletionCategories.contains(
                                    classification.completionCategory()))) {
                        return classification.completionCategory();
                    }
                }
            }
            if (!requestedCompletionCategories.isEmpty()) {
                for (SectionClassificationResponse classification : classifications) {
                    if (requestedCompletionCategories.contains(
                            classification.completionCategory())) {
                        return classification.completionCategory();
                    }
                }
            }
            for (SectionClassificationResponse classification : classifications) {
                if (classification.primary()) {
                    return classification.completionCategory();
                }
            }
            return classifications.isEmpty()
                    ? null
                    : classifications.get(0).completionCategory();
        }
    }

    private static final class MutableSession {

        private final SectionKey key;
        private final String day;
        private final int startMinute;
        private final int endMinute;
        private final String roomCode;
        private final String roomLabel;
        private final String buildingName;
        private final List<CourseRoomResponse> rooms = new ArrayList<>();

        private MutableSession(
                SectionKey key,
                String day,
                int startMinute,
                int endMinute,
                String roomCode,
                String roomLabel,
                String buildingName) {
            this.key = key;
            this.day = day;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.roomCode = roomCode;
            this.roomLabel = roomLabel;
            this.buildingName = buildingName;
        }

        private CourseSessionResponse toResponse() {
            return new CourseSessionResponse(
                    toDayOfWeek(day),
                    LocalTime.ofSecondOfDay(startMinute * 60L),
                    LocalTime.ofSecondOfDay(endMinute * 60L),
                    roomCode,
                    roomLabel,
                    buildingName,
                    List.copyOf(rooms));
        }
    }
}
