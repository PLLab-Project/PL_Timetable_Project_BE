package com.example.pl_timetable_project.academic.course;

import com.example.pl_timetable_project.academic.course.dto.CourseAcademicUnitResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseSessionResponse;
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
                findSessionsBySection(semesterId, courseCode);
        return jdbcTemplate.query("""
                SELECT semester_id, course_code, section_code, professor,
                       raw_lecture_time, time_to_be_announced,
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
                        rs.getBoolean("time_to_be_announced"),
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
                       raw_lecture_time, time_to_be_announced,
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
                rs.getBoolean("time_to_be_announced"),
                parseWarningCodes(rs.getArray("warning_codes"))))
                .stream()
                .findFirst();
        return section.map(row -> new SectionDetailResponse(
                row.semesterId(),
                row.courseCode(),
                row.sectionCode(),
                row.professor(),
                row.rawLectureTime(),
                row.timeToBeAnnounced(),
                row.warningCodes(),
                findSessions(semesterId, courseCode, sectionCode),
                findAcademicUnits(semesterId, courseCode, sectionCode)));
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

    private Map<String, List<CourseSessionResponse>> findSessionsBySection(
            String semesterId, String courseCode) {
        Map<String, List<CourseSessionResponse>> sessions = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT se.section_code, se.day, se.start_minute, se.end_minute,
                       se.room_code, r.label AS room_label, r.building_name
                  FROM sessions se
                  LEFT JOIN rooms r
                    ON r.semester_id = se.semester_id
                   AND r.code = se.room_code
                 WHERE se.semester_id = :semesterId
                   AND se.course_code = :courseCode
                 ORDER BY se.section_code, se.day, se.start_minute, se.id
                """, Map.of("semesterId", semesterId, "courseCode", courseCode), rs -> {
            sessions.computeIfAbsent(rs.getString("section_code"), ignored -> new ArrayList<>())
                    .add(toSession(
                            rs.getString("day"),
                            rs.getInt("start_minute"),
                            rs.getInt("end_minute"),
                            rs.getString("room_code"),
                            rs.getString("room_label"),
                            rs.getString("building_name")));
        });
        sessions.replaceAll((sectionCode, values) -> List.copyOf(values));
        return Map.copyOf(sessions);
    }

    private List<CourseSessionResponse> findSessions(
            String semesterId, String courseCode, String sectionCode) {
        return jdbcTemplate.query("""
                SELECT se.day, se.start_minute, se.end_minute,
                       se.room_code, r.label AS room_label, r.building_name
                  FROM sessions se
                  LEFT JOIN rooms r
                    ON r.semester_id = se.semester_id
                   AND r.code = se.room_code
                 WHERE se.semester_id = :semesterId
                   AND se.course_code = :courseCode
                   AND se.section_code = :sectionCode
                 ORDER BY se.day, se.start_minute, se.id
                """, Map.of(
                        "semesterId", semesterId,
                        "courseCode", courseCode,
                        "sectionCode", sectionCode),
                (rs, rowNum) -> toSession(
                        rs.getString("day"),
                        rs.getInt("start_minute"),
                        rs.getInt("end_minute"),
                        rs.getString("room_code"),
                        rs.getString("room_label"),
                        rs.getString("building_name")));
    }

    private CourseSessionResponse toSession(
            String day,
            int startMinute,
            int endMinute,
            String roomCode,
            String roomLabel,
            String buildingName) {
        return new CourseSessionResponse(
                toDayOfWeek(day),
                LocalTime.ofSecondOfDay(startMinute * 60L),
                LocalTime.ofSecondOfDay(endMinute * 60L),
                roomCode,
                roomLabel,
                buildingName);
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

    private record SectionRow(
            String semesterId,
            String courseCode,
            String sectionCode,
            String professor,
            String rawLectureTime,
            boolean timeToBeAnnounced,
            List<String> warningCodes) {
    }
}
