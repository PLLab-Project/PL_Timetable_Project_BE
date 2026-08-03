package com.example.pl_timetable_project.academic.section;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 시간표와 추천 기능이 학사 원본을 신뢰할 수 있도록 분반·수업시간을 DB에서 읽는다.
 */
@Repository
public class AcademicSectionQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AcademicSectionQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 학과 필터 없이 학기의 모든 분반을 읽는다. 시간표 수동 편집처럼 학과 제한이 없는 조회에 쓴다. */
    public Map<SectionReference, AcademicSection> findBySemesterId(String semesterId) {
        return findBySemesterId(semesterId, List.of());
    }

    /**
     * 학기의 분반을 읽되, academicUnitCodes가 비어 있지 않으면 학과 필터를 적용한다.
     *
     * <p>분반의 과목 category에 "교양"이 포함되거나 section_academic_units에
     * 연결된 학과가 없으면(미분류) 학과 무관으로 보고 항상 포함한다. 그 외에는
     * 연결된 학과 중 하나라도 academicUnitCodes에 있어야 포함한다.</p>
     */
    public Map<SectionReference, AcademicSection> findBySemesterId(
            String semesterId, List<String> academicUnitCodes) {
        Map<SectionReference, MutableSection> sections = new LinkedHashMap<>();

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("semesterId", semesterId)
                .addValue("academicUnitCodesEmpty", academicUnitCodes.isEmpty())
                .addValue(
                        "academicUnitCodes",
                        academicUnitCodes.isEmpty() ? List.of("") : academicUnitCodes);

        jdbcTemplate.query("""
                SELECT s.semester_id, s.course_code, s.section_code,
                       c.name AS course_name, c.category, s.professor, c.credits
                  FROM sections s
                  JOIN courses c
                    ON c.semester_id = s.semester_id
                   AND c.course_code = s.course_code
                 WHERE s.semester_id = :semesterId
                   AND (
                        CAST(:academicUnitCodesEmpty AS boolean) = true
                        OR c.category LIKE '%교양%'
                        OR NOT EXISTS (
                            SELECT 1
                              FROM section_academic_units sau
                             WHERE sau.semester_id = s.semester_id
                               AND sau.course_code = s.course_code
                               AND sau.section_code = s.section_code
                        )
                        OR EXISTS (
                            SELECT 1
                              FROM section_academic_units sau
                             WHERE sau.semester_id = s.semester_id
                               AND sau.course_code = s.course_code
                               AND sau.section_code = s.section_code
                               AND sau.academic_unit_code IN (:academicUnitCodes)
                        )
                   )
                 ORDER BY s.course_code, s.section_code
                """, parameters, rs -> {
            SectionReference reference = new SectionReference(
                    rs.getString("semester_id"),
                    rs.getString("course_code"),
                    rs.getString("section_code"));
            sections.put(reference, new MutableSection(
                    reference,
                    rs.getString("course_name"),
                    rs.getString("professor"),
                    rs.getBigDecimal("credits"),
                    rs.getString("category")));
        });

        jdbcTemplate.query("""
                SELECT semester_id, course_code, section_code, day, start_minute, end_minute
                  FROM sessions
                 WHERE semester_id = :semesterId
                 ORDER BY course_code, section_code, day, start_minute, id
                """, parameters, rs -> {
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
        });

        jdbcTemplate.query("""
                SELECT semester_id, course_code, section_code, academic_unit_code
                  FROM section_academic_units
                 WHERE semester_id = :semesterId
                """, parameters, rs -> {
            SectionReference reference = new SectionReference(
                    rs.getString("semester_id"),
                    rs.getString("course_code"),
                    rs.getString("section_code"));
            MutableSection section = sections.get(reference);
            if (section != null) {
                section.academicUnitCodes.add(rs.getString("academic_unit_code"));
            }
        });

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
        private final String category;
        private final List<AcademicMeeting> meetings = new ArrayList<>();
        private final List<String> academicUnitCodes = new ArrayList<>();

        private MutableSection(SectionReference reference, String courseName,
                               String professorName, BigDecimal credits, String category) {
            this.reference = reference;
            this.courseName = courseName;
            this.professorName = professorName;
            this.credits = credits;
            this.category = category;
        }

        private AcademicSection toAcademicSection() {
            boolean unrestricted = academicUnitCodes.isEmpty()
                    || (category != null && category.contains("교양"));
            return new AcademicSection(
                    reference,
                    courseName,
                    professorName,
                    credits,
                    meetings,
                    unrestricted ? List.of() : academicUnitCodes);
        }
    }
}
