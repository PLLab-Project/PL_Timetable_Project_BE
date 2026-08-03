package com.example.pl_timetable_project.completedcourse.repository;

import com.example.pl_timetable_project.academic.course.dto.CourseRoomResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseSessionResponse;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CompletedCourseOcrMatchRepository {

    private static final Pattern HISTORICAL_TIME_PATTERN = Pattern.compile(
            "([월화수목금토일])\\s*(\\d{1,2}):(\\d{2})\\s*[-~]\\s*"
                    + "(\\d{1,2}):(\\d{2})");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CompletedCourseOcrMatchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SectionCandidate> findCandidates(
            Set<String> semesterIds, Set<String> normalizedCourseNames) {
        return findCandidates(semesterIds, normalizedCourseNames, null);
    }

    public List<SectionCandidate> findCandidates(
            Set<String> semesterIds,
            Set<String> normalizedCourseNames,
            String preferredAcademicUnitCode) {
        if (semesterIds.isEmpty() || normalizedCourseNames.isEmpty()) {
            return List.of();
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("semesterIds", semesterIds);
        parameters.put("normalizedCourseNames", normalizedCourseNames);
        parameters.put(
                "hasPreferredAcademicUnitCode",
                preferredAcademicUnitCode != null);
        parameters.put("preferredAcademicUnitCode", preferredAcademicUnitCode);
        List<SectionRow> rows = jdbcTemplate.query("""
                SELECT section.semester_id, section.course_code,
                       course.name AS course_name, section.section_code,
                       historical.id AS historical_offering_id,
                       section.professor, course.category, course.credits,
                       section.raw_lecture_time, section.raw_location,
                       false AS historical_only,
                       (
                           SELECT classification.completion_category
                             FROM section_classification_contexts classification
                            WHERE classification.semester_id = section.semester_id
                              AND classification.course_code = section.course_code
                              AND classification.section_code = section.section_code
                              AND classification.completion_category IS NOT NULL
                              AND :hasPreferredAcademicUnitCode
                              AND classification.academic_unit_code =
                                  :preferredAcademicUnitCode
                            ORDER BY classification.is_primary DESC,
                                     classification.source_page,
                                     classification.source_row
                            LIMIT 1
                       ) AS completion_category
                  FROM sections section
                  JOIN courses course
                    ON course.semester_id = section.semester_id
                   AND course.course_code = section.course_code
                  LEFT JOIN historical_course_offerings historical
                    ON historical.academic_year::text =
                           split_part(section.semester_id, '-', 1)
                   AND historical.term_code =
                           split_part(section.semester_id, '-', 2)
                   AND historical.course_code = section.course_code
                   AND historical.section_code = section.section_code
                 WHERE section.semester_id IN (:semesterIds)
                   AND regexp_replace(
                           lower(course.name),
                           '[^0-9a-z가-힣]',
                           '',
                           'g') IN (:normalizedCourseNames)
                UNION ALL
                SELECT historical.academic_year::text || '-' ||
                           historical.term_code AS semester_id,
                       historical.course_code,
                       historical.korean_name AS course_name,
                       historical.section_code,
                       historical.id AS historical_offering_id,
                       historical.professor_name AS professor,
                       historical.completion_category AS category,
                       historical.credits,
                       historical.raw_lecture_time,
                       historical.raw_location,
                       true AS historical_only,
                       historical.completion_category
                  FROM historical_course_offerings historical
                 WHERE historical.academic_year::text || '-' ||
                           historical.term_code IN (:semesterIds)
                   AND regexp_replace(
                           lower(historical.korean_name),
                           '[^0-9a-z가-힣]',
                           '',
                           'g') IN (:normalizedCourseNames)
                   AND NOT EXISTS (
                       SELECT 1
                         FROM sections section
                        WHERE section.semester_id =
                                  historical.academic_year::text || '-' ||
                                  historical.term_code
                          AND section.course_code = historical.course_code
                          AND section.section_code = historical.section_code
                   )
                """, parameters, (result, rowNumber) -> new SectionRow(
                result.getString("semester_id"),
                result.getString("course_code"),
                result.getString("course_name"),
                result.getString("section_code"),
                result.getString("historical_offering_id"),
                result.getString("professor"),
                result.getString("category"),
                result.getString("completion_category"),
                result.getBigDecimal("credits"),
                result.getString("raw_lecture_time"),
                result.getString("raw_location"),
                result.getBoolean("historical_only")));
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<SectionKey> keys = new LinkedHashSet<>();
        rows.stream()
                .filter(row -> !row.historicalOnly())
                .forEach(row -> keys.add(row.key()));
        Map<SectionKey, List<CourseSessionResponse>> sessions = findSessions(keys);
        return rows.stream()
                .map(row -> row.toCandidate(
                        row.historicalOnly()
                                ? historicalSessions(
                                        row.rawLectureTime(), row.rawLocation())
                                : sessions.getOrDefault(row.key(), List.of())))
                .sorted(Comparator.comparing(SectionCandidate::semesterId)
                        .reversed()
                        .thenComparing(SectionCandidate::courseName)
                        .thenComparing(SectionCandidate::courseCode)
                        .thenComparing(SectionCandidate::sectionCode))
                .toList();
    }

    public List<String> findHistoricalSemesterIds() {
        return jdbcTemplate.getJdbcTemplate().queryForList("""
                SELECT DISTINCT academic_year::text || '-' || term_code
                  FROM historical_term_datasets
                 ORDER BY academic_year::text || '-' || term_code DESC
                """, String.class);
    }

    public Optional<HistoricalOfferingReference> findHistoricalOffering(String offeringId) {
        List<HistoricalOfferingReference> offerings = jdbcTemplate.query("""
                SELECT id,
                       academic_year::text || '-' || term_code AS semester_id,
                       course_code, section_code, korean_name, professor_name,
                       completion_category, credits, raw_lecture_time, raw_location
                  FROM historical_course_offerings
                 WHERE id = :offeringId
                """, Map.of("offeringId", offeringId), (result, rowNumber) ->
                new HistoricalOfferingReference(
                        result.getString("id"),
                        result.getString("semester_id"),
                        result.getString("course_code"),
                        result.getString("section_code"),
                        result.getString("korean_name"),
                        result.getString("professor_name"),
                        result.getString("completion_category"),
                        result.getBigDecimal("credits"),
                        result.getString("raw_lecture_time"),
                        result.getString("raw_location")));
        return offerings.stream().findFirst();
    }

    private Map<SectionKey, List<CourseSessionResponse>> findSessions(
            Set<SectionKey> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        Set<String> semesterIds = new LinkedHashSet<>();
        Set<String> courseCodes = new LinkedHashSet<>();
        keys.forEach(key -> {
            semesterIds.add(key.semesterId());
            courseCodes.add(key.courseCode());
        });

        Map<Long, MutableSession> sessions = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT session.id, session.semester_id, session.course_code,
                       session.section_code, session.day,
                       session.start_minute, session.end_minute,
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
                 WHERE session.semester_id IN (:semesterIds)
                   AND session.course_code IN (:courseCodes)
                 ORDER BY session.semester_id, session.course_code,
                          session.section_code, session.sequence_no,
                          session.id, linked.position
                """, Map.of(
                        "semesterIds", semesterIds,
                        "courseCodes", courseCodes),
                result -> {
                    SectionKey key = new SectionKey(
                            result.getString("semester_id"),
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

    private static List<CourseSessionResponse> historicalSessions(
            String rawLectureTime, String rawLocation) {
        if (rawLectureTime == null || rawLectureTime.isBlank()) {
            return List.of();
        }
        String roomLabel = rawLocation == null || rawLocation.isBlank()
                ? null
                : rawLocation.strip();
        List<CourseSessionResponse> sessions = new ArrayList<>();
        Matcher matcher = HISTORICAL_TIME_PATTERN.matcher(rawLectureTime);
        while (matcher.find()) {
            int startHour = Integer.parseInt(matcher.group(2));
            int startMinute = Integer.parseInt(matcher.group(3));
            int endHour = Integer.parseInt(matcher.group(4));
            int endMinute = Integer.parseInt(matcher.group(5));
            if (startHour > 23 || endHour > 23
                    || startMinute > 59 || endMinute > 59) {
                continue;
            }
            sessions.add(new CourseSessionResponse(
                    toDayOfWeek(matcher.group(1)),
                    LocalTime.of(startHour, startMinute),
                    LocalTime.of(endHour, endMinute),
                    null,
                    roomLabel,
                    null,
                    List.of()));
        }
        return List.copyOf(sessions);
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
                    "지원하지 않는 수업 요일입니다: " + day);
        };
    }

    public record SectionCandidate(
            String semesterId,
            String courseCode,
            String courseName,
            String sectionCode,
            String professor,
            String category,
            String completionCategory,
            BigDecimal credits,
            String rawLectureTime,
            String rawLocation,
            List<CourseSessionResponse> sessions,
            String historicalOfferingId) {
    }

    public record HistoricalOfferingReference(
            String id,
            String semesterId,
            String courseCode,
            String sectionCode,
            String courseName,
            String professor,
            String completionCategory,
            BigDecimal credits,
            String rawLectureTime,
            String rawLocation) {
    }

    private record SectionKey(
            String semesterId, String courseCode, String sectionCode) {
    }

    private record SectionRow(
            String semesterId,
            String courseCode,
            String courseName,
            String sectionCode,
            String historicalOfferingId,
            String professor,
            String category,
            String completionCategory,
            BigDecimal credits,
            String rawLectureTime,
            String rawLocation,
            boolean historicalOnly) {

        private SectionKey key() {
            return new SectionKey(semesterId, courseCode, sectionCode);
        }

        private SectionCandidate toCandidate(List<CourseSessionResponse> sessions) {
            return new SectionCandidate(
                    semesterId,
                    courseCode,
                    courseName,
                    sectionCode,
                    professor,
                    category,
                    completionCategory,
                    credits,
                    rawLectureTime,
                    rawLocation,
                    sessions,
                    historicalOfferingId);
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
