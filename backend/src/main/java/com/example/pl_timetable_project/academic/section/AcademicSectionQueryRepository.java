package com.example.pl_timetable_project.academic.section;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 시간표와 추천 기능이 학사 원본을 신뢰할 수 있도록 분반·수업시간을 DB에서 읽는다.
 */
@Repository
public class AcademicSectionQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public AcademicSectionQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<SectionReference, AcademicSection> findBySemesterId(String semesterId) {
        Map<SectionReference, MutableSection> sections = new LinkedHashMap<>();

        jdbcTemplate.query("""
                SELECT s.semester_id, s.course_code, s.section_code,
                       c.name AS course_name, s.professor, c.credits
                  FROM sections s
                  JOIN courses c
                    ON c.semester_id = s.semester_id
                   AND c.course_code = s.course_code
                 WHERE s.semester_id = ?
                 ORDER BY s.course_code, s.section_code
                """, rs -> {
            SectionReference reference = new SectionReference(
                    rs.getString("semester_id"),
                    rs.getString("course_code"),
                    rs.getString("section_code"));
            sections.put(reference, new MutableSection(
                    reference,
                    rs.getString("course_name"),
                    rs.getString("professor"),
                    rs.getBigDecimal("credits")));
        }, semesterId);

        jdbcTemplate.query("""
                SELECT semester_id, course_code, section_code, day, start_minute, end_minute
                  FROM sessions
                 WHERE semester_id = ?
                 ORDER BY course_code, section_code, day, start_minute, id
                """, rs -> {
            SectionReference reference = new SectionReference(
                    rs.getString("semester_id"),
                    rs.getString("course_code"),
                    rs.getString("section_code"));
            MutableSection section = sections.get(reference);
            if (section != null) {
                section.meetings.add(new AcademicMeeting(
                        toDayOfWeek(rs.getString("day")),
                        toLocalTime(rs.getInt("start_minute")),
                        toLocalTime(rs.getInt("end_minute"))));
            }
        }, semesterId);

        Map<SectionReference, AcademicSection> result = new LinkedHashMap<>();
        sections.forEach((reference, section) -> result.put(reference, section.toAcademicSection()));
        return Map.copyOf(result);
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
            default -> throw new IllegalStateException("지원하지 않는 수업 요일입니다: " + day);
        };
    }

    private static LocalTime toLocalTime(int minuteOfDay) {
        return LocalTime.ofSecondOfDay(minuteOfDay * 60L);
    }

    private static final class MutableSection {

        private final SectionReference reference;
        private final String courseName;
        private final String professorName;
        private final BigDecimal credits;
        private final List<AcademicMeeting> meetings = new ArrayList<>();

        private MutableSection(SectionReference reference, String courseName,
                               String professorName, BigDecimal credits) {
            this.reference = reference;
            this.courseName = courseName;
            this.professorName = professorName;
            this.credits = credits;
        }

        private AcademicSection toAcademicSection() {
            return new AcademicSection(reference, courseName, professorName, credits, meetings);
        }
    }
}
