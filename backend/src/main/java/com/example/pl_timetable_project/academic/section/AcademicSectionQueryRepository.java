package com.example.pl_timetable_project.academic.section;

import com.example.pl_timetable_project.academic.common.LiberalAreaCode;
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

    /**
     * "OO학과만신청가능"처럼 그 뒤에 "/"나 다른 문자 없이 정확히 끝나는 분반만
     * 진짜 학과 제한 후보로 본다. "OO학과만신청가능/셋째날해제"처럼 "/" 뒤에
     * 시간 개방 문구가 붙은 경우는 실데이터 검증 결과 이 정규식에 전혀 매치되지
     * 않는다 — 그런 항목은 문자열 끝이 "해제"/"전체"이지 "만신청가능"/
     * "만수강가능"이 아니기 때문이다.
     *
     * <p>학과명 앞뒤로 "유학생"/"외국인"/"중국인"/"복수전공"/"교직"/"이수자"/
     * "전용분반" 같은 키워드가 있으면 순수한 학과 제한이 아니므로 제외한다
     * (예: 유학생 전용, 복수전공생 포함, 교직 이수자 등 — 학과 코드 하나로
     * 판정할 수 없는 별도 조건).</p>
     */
    private static final String HARD_RESTRICTION_CANDIDATE_SQL = """
            SELECT s.semester_id, s.course_code, s.section_code,
                   regexp_replace(s.notes, '(만신청가능|만수강가능)$', '') AS prefix
              FROM sections s
             WHERE s.semester_id = :semesterId
               AND s.notes IS NOT NULL
               AND s.notes ~ '(만신청가능|만수강가능)$'
               AND s.notes !~ '(유학생|외국인|중국인|복수전공|교직|이수자|전용분반)'
            """;

    /**
     * 위 후보의 prefix가 academic_units.name 또는 academic_unit_aliases.alias를
     * 부분 문자열로 포함하면 그 학과로 확정한다. 여러 학과가 동시에 매치되면
     * (예: 짧은 이름이 긴 이름의 일부인 경우) 가장 긴 이름/별칭을 우선한다.
     * 어떤 학과명도 못 찾으면(학년만 있거나 프로그램명 등) 안전하게 제외 대상이
     * 아닌 것으로 남긴다 — 결과 맵에 아예 나타나지 않는다.
     */
    private static final String HARD_RESTRICTION_RESOLUTION_SQL = """
            WITH candidates AS (
                %s
            ),
            matched AS (
                SELECT c.semester_id, c.course_code, c.section_code,
                       unit.code AS academic_unit_code, length(unit.name) AS match_len
                  FROM candidates c
                  JOIN academic_units unit
                    ON c.prefix ILIKE '%%' || unit.name || '%%'
                UNION ALL
                SELECT c.semester_id, c.course_code, c.section_code,
                       alias.academic_unit_code, length(alias.alias) AS match_len
                  FROM candidates c
                  JOIN academic_unit_aliases alias
                    ON c.prefix ILIKE '%%' || alias.alias || '%%'
            ),
            ranked AS (
                SELECT semester_id, course_code, section_code, academic_unit_code,
                       row_number() OVER (
                           PARTITION BY semester_id, course_code, section_code
                           ORDER BY match_len DESC
                       ) AS rn
                  FROM matched
            )
            SELECT semester_id, course_code, section_code, academic_unit_code
              FROM ranked
             WHERE rn = 1
            """.formatted(HARD_RESTRICTION_CANDIDATE_SQL);

    public AcademicSectionQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 학기의 모든 분반을 읽는다. 학과 필터로 후보를 배제하지 않는다 — 학과
     * 연관성은 {@link AcademicSection#restrictedAcademicUnitCodes()}에 구조화된
     * 정보로만 담아 반환하고, 실제로 우선순위를 줄지 배제할지는 호출부(자동편성
     * 점수 계산 등)가 판단한다.
     */
    public Map<SectionReference, AcademicSection> findBySemesterId(String semesterId) {
        Map<SectionReference, MutableSection> sections = new LinkedHashMap<>();

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("semesterId", semesterId);

        jdbcTemplate.query("""
                SELECT s.semester_id, s.course_code, s.section_code,
                       c.name AS course_name, c.category, s.professor, c.credits
                  FROM sections s
                  JOIN courses c
                    ON c.semester_id = s.semester_id
                   AND c.course_code = s.course_code
                 WHERE s.semester_id = :semesterId
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

        jdbcTemplate.query(HARD_RESTRICTION_RESOLUTION_SQL, parameters, rs -> {
            SectionReference reference = new SectionReference(
                    rs.getString("semester_id"),
                    rs.getString("course_code"),
                    rs.getString("section_code"));
            MutableSection section = sections.get(reference);
            if (section != null) {
                section.hardRestrictedAcademicUnitCode = rs.getString("academic_unit_code");
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
        private String hardRestrictedAcademicUnitCode;

        private MutableSection(SectionReference reference, String courseName,
                               String professorName, BigDecimal credits, String category) {
            this.reference = reference;
            this.courseName = courseName;
            this.professorName = professorName;
            this.credits = credits;
            this.category = category;
        }

        private AcademicSection toAcademicSection() {
            boolean liberalCredit = category != null && category.contains("교양");
            boolean unrestricted = academicUnitCodes.isEmpty() || liberalCredit;
            return new AcademicSection(
                    reference,
                    courseName,
                    professorName,
                    credits,
                    meetings,
                    unrestricted ? List.of() : academicUnitCodes,
                    LiberalAreaCode.parse(category),
                    hardRestrictedAcademicUnitCode,
                    liberalCredit);
        }
    }
}
