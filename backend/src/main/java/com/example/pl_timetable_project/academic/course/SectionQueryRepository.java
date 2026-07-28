package com.example.pl_timetable_project.academic.course;

import com.example.pl_timetable_project.academic.course.dto.CourseAcademicUnitResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseRoomResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseSessionResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionClassificationResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionDetailResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionSummaryResponse;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SectionQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SectionQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SectionSummaryResponse> findAll(
            String semesterId, String courseCode) {
        Map<String, List<CourseSessionResponse>> sessions =
                findSessionsBySection(semesterId, courseCode, null);
        return jdbcTemplate.query("""
                SELECT semester_id, course_code, section_code, professor,
                       raw_lecture_time, raw_location, time_to_be_announced,
                       target_grade, capacity, notes,
                       jsonb_array_length(warning_codes) AS warning_count
                  FROM sections
                 WHERE semester_id = :semesterId
                   AND course_code = :courseCode
                 ORDER BY section_code
                """, Map.of("semesterId", semesterId, "courseCode", courseCode),
                (rs, rowNum) -> new SectionSummaryResponse(
                        rs.getString("semester_id"),
                        rs.getString("course_code"),
                        rs.getString("section_code"),
                        rs.getString("professor"),
                        rs.getString("raw_lecture_time"),
                        rs.getString("raw_location"),
                        rs.getBoolean("time_to_be_announced"),
                        rs.getString("target_grade"),
                        rs.getObject("capacity", Integer.class),
                        rs.getString("notes"),
                        rs.getInt("warning_count"),
                        sessions.getOrDefault(rs.getString("section_code"), List.of())));
    }

    public Optional<SectionDetailResponse> findById(
            String semesterId, String courseCode, String sectionCode) {
        Map<String, ?> parameters = Map.of(
                "semesterId", semesterId,
                "courseCode", courseCode,
                "sectionCode", sectionCode);
        Optional<SectionRow> section = jdbcTemplate.query("""
                SELECT semester_id, course_code, section_code, professor,
                       raw_lecture_time, raw_location, time_to_be_announced,
                       target_grade, capacity, notes, source_page, source_row,
                       ARRAY(
                           SELECT jsonb_array_elements_text(s.warning_codes)
                       ) AS warning_codes
                  FROM sections s
                 WHERE s.semester_id = :semesterId
                   AND s.course_code = :courseCode
                   AND s.section_code = :sectionCode
                """, parameters, (rs, rowNum) -> new SectionRow(
                rs.getString("semester_id"),
                rs.getString("course_code"),
                rs.getString("section_code"),
                rs.getString("professor"),
                rs.getString("raw_lecture_time"),
                rs.getString("raw_location"),
                rs.getBoolean("time_to_be_announced"),
                rs.getString("target_grade"),
                rs.getObject("capacity", Integer.class),
                rs.getString("notes"),
                rs.getObject("source_page", Integer.class),
                rs.getObject("source_row", Integer.class),
                parseWarningCodes(rs.getArray("warning_codes"))))
                .stream()
                .findFirst();
        return section.map(row -> new SectionDetailResponse(
                row.semesterId(),
                row.courseCode(),
                row.sectionCode(),
                row.professor(),
                row.rawLectureTime(),
                row.rawLocation(),
                row.timeToBeAnnounced(),
                row.targetGrade(),
                row.capacity(),
                row.notes(),
                row.sourcePage(),
                row.sourceRow(),
                row.warningCodes(),
                findSessionsBySection(semesterId, courseCode, sectionCode)
                        .getOrDefault(sectionCode, List.of()),
                findAcademicUnits(semesterId, courseCode, sectionCode),
                findClassifications(semesterId, courseCode, sectionCode)));
    }

    private List<CourseAcademicUnitResponse> findAcademicUnits(
            String semesterId, String courseCode, String sectionCode) {
        return jdbcTemplate.query("""
                SELECT u.code, u.name, sau.relation_type
                  FROM section_academic_units sau
                  JOIN academic_units u ON u.code = sau.academic_unit_code
                 WHERE sau.semester_id = :semesterId
                   AND sau.course_code = :courseCode
                   AND sau.section_code = :sectionCode
                 ORDER BY u.name, u.code, sau.relation_type
                """, Map.of(
                        "semesterId", semesterId,
                        "courseCode", courseCode,
                        "sectionCode", sectionCode),
                (rs, rowNum) -> new CourseAcademicUnitResponse(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("relation_type")));
    }

    private List<SectionClassificationResponse> findClassifications(
            String semesterId, String courseCode, String sectionCode) {
        return jdbcTemplate.query("""
                SELECT context_label, context_kind, academic_unit_code,
                       completion_category, target_grade, is_primary,
                       is_shaded, source_page
                  FROM section_classification_contexts
                 WHERE semester_id = :semesterId
                   AND course_code = :courseCode
                   AND section_code = :sectionCode
                 ORDER BY is_primary DESC, source_page, source_row
                """, Map.of(
                        "semesterId", semesterId,
                        "courseCode", courseCode,
                        "sectionCode", sectionCode),
                (rs, rowNum) -> new SectionClassificationResponse(
                        rs.getString("context_label"),
                        rs.getString("context_kind"),
                        rs.getString("academic_unit_code"),
                        rs.getString("completion_category"),
                        rs.getString("target_grade"),
                        rs.getBoolean("is_primary"),
                        rs.getBoolean("is_shaded"),
                        rs.getInt("source_page")));
    }

    private Map<String, List<CourseSessionResponse>> findSessionsBySection(
            String semesterId, String courseCode, String sectionCode) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("semesterId", semesterId)
                .addValue("courseCode", courseCode)
                .addValue("sectionCode", sectionCode);
        String sectionFilter = sectionCode == null
                ? ""
                : " AND se.section_code = :sectionCode";
        Map<Long, MutableSession> sessions = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT se.id, se.section_code, se.day, se.start_minute,
                       se.end_minute, se.room_code,
                       primary_room.label AS room_label,
                       primary_room.building_name,
                       linked.position AS linked_position,
                       linked.room_code AS linked_room_code,
                       linked_room.label AS linked_room_label,
                       linked_room.building_name AS linked_building_name
                  FROM sessions se
                  LEFT JOIN rooms primary_room
                    ON primary_room.semester_id = se.semester_id
                   AND primary_room.code = se.room_code
                  LEFT JOIN session_rooms linked
                    ON linked.session_id = se.id
                   AND linked.semester_id = se.semester_id
                  LEFT JOIN rooms linked_room
                    ON linked_room.semester_id = linked.semester_id
                   AND linked_room.code = linked.room_code
                 WHERE se.semester_id = :semesterId
                   AND se.course_code = :courseCode
                """ + sectionFilter + """
                 ORDER BY se.section_code, se.sequence_no, se.id, linked.position
                """, parameters, rs -> {
            long sessionId = rs.getLong("id");
            String rowSectionCode = rs.getString("section_code");
            String day = rs.getString("day");
            int startMinute = rs.getInt("start_minute");
            int endMinute = rs.getInt("end_minute");
            String roomCode = rs.getString("room_code");
            String roomLabel = rs.getString("room_label");
            String buildingName = rs.getString("building_name");
            MutableSession session = sessions.computeIfAbsent(
                    sessionId,
                    ignored -> new MutableSession(
                            rowSectionCode,
                            day,
                            startMinute,
                            endMinute,
                            roomCode,
                            roomLabel,
                            buildingName));
            Integer position = rs.getObject("linked_position", Integer.class);
            if (position != null) {
                session.rooms.add(new CourseRoomResponse(
                        position,
                        rs.getString("linked_room_code"),
                        rs.getString("linked_room_label"),
                        rs.getString("linked_building_name")));
            }
        });

        Map<String, List<CourseSessionResponse>> bySection = new LinkedHashMap<>();
        for (MutableSession session : sessions.values()) {
            bySection.computeIfAbsent(
                    session.sectionCode, ignored -> new ArrayList<>())
                    .add(session.toResponse());
        }
        bySection.replaceAll((ignored, values) -> List.copyOf(values));
        return Map.copyOf(bySection);
    }

    private DayOfWeek toDayOfWeek(String day) {
        return switch (day) {
            case "월" -> DayOfWeek.MONDAY;
            case "화" -> DayOfWeek.TUESDAY;
            case "수" -> DayOfWeek.WEDNESDAY;
            case "목" -> DayOfWeek.THURSDAY;
            case "금" -> DayOfWeek.FRIDAY;
            case "토" -> DayOfWeek.SATURDAY;
            case "일" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalStateException("지원하지 않는 수업 요일입니다: " + day);
        };
    }

    private List<String> parseWarningCodes(java.sql.Array warnings) {
        try {
            return List.copyOf(java.util.Arrays.asList(
                    (String[]) warnings.getArray()));
        } catch (SQLException exception) {
            throw new IllegalStateException("분반 경고 코드를 읽을 수 없습니다.", exception);
        }
    }

    private final class MutableSession {

        private final String sectionCode;
        private final String day;
        private final int startMinute;
        private final int endMinute;
        private final String roomCode;
        private final String roomLabel;
        private final String buildingName;
        private final List<CourseRoomResponse> rooms = new ArrayList<>();

        private MutableSession(
                String sectionCode,
                String day,
                int startMinute,
                int endMinute,
                String roomCode,
                String roomLabel,
                String buildingName) {
            this.sectionCode = sectionCode;
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

    private record SectionRow(
            String semesterId,
            String courseCode,
            String sectionCode,
            String professor,
            String rawLectureTime,
            String rawLocation,
            boolean timeToBeAnnounced,
            String targetGrade,
            Integer capacity,
            String notes,
            Integer sourcePage,
            Integer sourceRow,
            List<String> warningCodes) {
    }
}
