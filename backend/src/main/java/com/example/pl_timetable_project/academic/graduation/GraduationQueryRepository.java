package com.example.pl_timetable_project.academic.graduation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GraduationQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GraduationQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RuleProfile> findRule(RuleScope scope) {
        MapSqlParameterSource parameters = scopeParameters(scope);
        return jdbcTemplate.query("""
                SELECT p.id, p.dataset_id, p.source_rule_id,
                       p.admission_year, p.academic_unit, p.academic_unit_key,
                       p.academic_unit_code, p.student_type, p.program_path,
                       p.total_credits_min, p.major_foundation_min,
                       p.major_required_min, p.major_elective_min,
                       p.additional_major_min, p.primary_major_min,
                       p.secondary_program_min, p.requires_manual_review,
                       p.liberal_requirement_set_id,
                       liberal.required_credits_min,
                       liberal.elective_credits_min,
                       liberal.total_credits_min AS liberal_total_credits_min,
                       liberal.total_credits_max AS liberal_total_credits_max,
                       dataset.source_path
                  FROM graduation_credit_profiles p
                  JOIN requirement_datasets dataset ON dataset.id = p.dataset_id
                  JOIN graduation_liberal_requirement_sets liberal
                    ON liberal.id = p.liberal_requirement_set_id
                 WHERE p.admission_year = :admissionYear
                   AND upper(p.student_type) = :studentType
                   AND upper(p.program_path) = :programPath
                   AND (
                        p.academic_unit_code = :academicUnit
                        OR p.academic_unit_key =
                           normalize_academic_unit_key(:academicUnit)
                        OR EXISTS (
                            SELECT 1
                              FROM graduation_credit_profile_academic_unit_aliases alias
                             WHERE alias.profile_id = p.id
                               AND alias.alias_key =
                                   normalize_academic_unit_key(:academicUnit)
                        )
                   )
                 ORDER BY dataset.imported_at DESC, p.id
                 LIMIT 1
                """, parameters, (rs, rowNum) -> new RuleProfile(
                rs.getString("id"),
                rs.getString("dataset_id"),
                rs.getString("source_rule_id"),
                rs.getInt("admission_year"),
                rs.getString("academic_unit"),
                rs.getString("academic_unit_key"),
                rs.getString("academic_unit_code"),
                rs.getString("student_type"),
                rs.getString("program_path"),
                rs.getInt("total_credits_min"),
                rs.getInt("major_foundation_min"),
                rs.getInt("major_required_min"),
                rs.getInt("major_elective_min"),
                rs.getObject("additional_major_min", Integer.class),
                rs.getInt("primary_major_min"),
                rs.getObject("secondary_program_min", Integer.class),
                rs.getBoolean("requires_manual_review"),
                rs.getString("liberal_requirement_set_id"),
                rs.getInt("required_credits_min"),
                rs.getInt("elective_credits_min"),
                rs.getInt("liberal_total_credits_min"),
                rs.getObject("liberal_total_credits_max", Integer.class),
                rs.getString("source_path")))
                .stream()
                .findFirst();
    }

    public Optional<StudentScope> findStudentScope(UUID userId) {
        return jdbcTemplate.query("""
                SELECT admission_year, academic_unit_key, academic_unit_name,
                       student_type, program_path
                  FROM student_profiles
                 WHERE user_id = :userId
                """, Map.of("userId", userId), (rs, rowNum) -> new StudentScope(
                rs.getObject("admission_year", Integer.class),
                rs.getString("academic_unit_key"),
                rs.getString("academic_unit_name"),
                rs.getString("student_type"),
                rs.getString("program_path")))
                .stream()
                .findFirst();
    }

    public List<AreaRule> findAreaRules(String liberalRequirementSetId) {
        return jdbcTemplate.query("""
                SELECT area, min_courses, min_credits
                  FROM graduation_liberal_area_requirements
                 WHERE requirement_set_id = :requirementSetId
                 ORDER BY "position", area
                """, Map.of("requirementSetId", liberalRequirementSetId),
                (rs, rowNum) -> new AreaRule(
                        rs.getString("area"),
                        rs.getInt("min_courses"),
                        rs.getObject("min_credits", Integer.class)));
    }

    public List<RequiredCourseRule> findRequiredCourses(RuleProfile profile) {
        List<RequiredCourseRule> result =
                new ArrayList<>(findLiberalRequiredCourses(profile));
        result.addAll(findMajorRequiredCourses(profile));
        return result;
    }

    private List<RequiredCourseRule> findLiberalRequiredCourses(
            RuleProfile profile) {
        return jdbcTemplate.query("""
                SELECT course.id, 'LIBERAL_REQUIRED' AS requirement_type,
                       course.course_code, course.course_name,
                       course.credits::numeric AS credits, course.grade,
                       'page:' || course.source_page AS source_ref
                  FROM graduation_liberal_required_courses course
                 WHERE course.requirement_set_id = :requirementSetId
                 ORDER BY course."position", course.course_name
                """, Map.of("requirementSetId", profile.liberalRequirementSetId()),
                (rs, rowNum) -> new RequiredCourseRule(
                        rs.getString("id"),
                        rs.getString("requirement_type"),
                        rs.getString("course_code"),
                        rs.getString("course_name"),
                        findLiberalCourseAliases(rs.getString("id")),
                        rs.getBigDecimal("credits"),
                        rs.getObject("grade", Integer.class),
                        rs.getString("source_ref")));
    }

    private List<String> findLiberalCourseAliases(String courseId) {
        return jdbcTemplate.queryForList("""
                SELECT alias
                  FROM graduation_liberal_course_aliases
                 WHERE course_id = :courseId
                 ORDER BY "position", alias
                """, Map.of("courseId", courseId), String.class);
    }

    private List<RequiredCourseRule> findMajorRequiredCourses(
            RuleProfile profile) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("admissionYear", profile.admissionYear())
                .addValue("academicUnitKey", profile.academicUnitKey());
        return jdbcTemplate.query("""
                WITH selected_program AS (
                    SELECT program.id
                      FROM curriculum_program_requirements program
                      JOIN requirement_datasets dataset
                        ON dataset.id = program.dataset_id
                     WHERE program.admission_year = :admissionYear
                       AND program.academic_unit_key = :academicUnitKey
                     ORDER BY dataset.imported_at DESC, program.id
                     LIMIT 1
                )
                SELECT course.id,
                       CASE course.classification
                           WHEN '전기' THEN 'MAJOR_FOUNDATION_REQUIRED'
                           ELSE 'MAJOR_REQUIRED'
                       END AS requirement_type,
                       course.course_code, course.course_name,
                       course.credits::numeric AS credits, course.grade,
                       course.source_locator::text AS source_ref
                  FROM curriculum_required_courses course
                  JOIN selected_program program ON program.id = course.program_id
                 ORDER BY course.classification, course.grade NULLS LAST,
                          course.course_code
                """, parameters, (rs, rowNum) -> new RequiredCourseRule(
                rs.getString("id"),
                rs.getString("requirement_type"),
                rs.getString("course_code"),
                rs.getString("course_name"),
                List.of(),
                rs.getBigDecimal("credits"),
                rs.getObject("grade", Integer.class),
                rs.getString("source_ref")));
    }

    public List<String> findSourceRefs(RuleProfile profile) {
        return jdbcTemplate.queryForList("""
                SELECT source_ref
                  FROM graduation_credit_profile_source_refs
                 WHERE profile_id = :profileId
                 ORDER BY "position"
                """, Map.of("profileId", profile.id()), String.class);
    }

    public List<RuleWarning> findWarnings(RuleProfile profile) {
        return jdbcTemplate.query("""
                SELECT code, calculated, printed
                  FROM graduation_credit_profile_warnings
                 WHERE profile_id = :profileId
                 ORDER BY "position", code
                """, Map.of("profileId", profile.id()),
                (rs, rowNum) -> new RuleWarning(
                        rs.getString("code"),
                        rs.getInt("calculated"),
                        rs.getInt("printed")));
    }

    public List<ManualRule> findManualRules(RuleProfile profile) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("admissionYear", profile.admissionYear())
                .addValue("academicUnitKey", profile.academicUnitKey())
                .addValue("academicUnitCode", profile.academicUnitCode())
                .addValue("studentType", profile.studentType())
                .addValue("programPath", profile.programPath());
        List<ManualRule> result = new ArrayList<>(jdbcTemplate.query("""
                SELECT rule.id AS code, rule.rule_kind AS title,
                       coalesce(rule.description, rule.rule_kind) AS description,
                       dataset.source_path AS source_ref
                  FROM graduation_requirement_rules rule
                  JOIN requirement_datasets dataset ON dataset.id = rule.dataset_id
                 WHERE rule.requires_manual_review = true
                   AND (
                        rule.academic_unit_key IS NULL
                        OR rule.academic_unit_key = :academicUnitKey
                        OR rule.academic_unit_code = :academicUnitCode
                   )
                   AND (
                        rule.admission_year_start IS NULL
                        OR rule.admission_year_start <= :admissionYear
                   )
                   AND (
                        rule.admission_year_end IS NULL
                        OR rule.admission_year_end >= :admissionYear
                   )
                   AND (
                        rule.student_type IS NULL
                        OR upper(rule.student_type) = upper(:studentType)
                   )
                   AND (
                        rule.program_path IS NULL
                        OR upper(rule.program_path) = upper(:programPath)
                   )
                 ORDER BY rule.rule_kind, rule.id
                """, parameters, (rs, rowNum) -> new ManualRule(
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("source_ref"))));
        result.addAll(jdbcTemplate.query("""
                WITH selected_profile AS (
                    SELECT profile.id
                      FROM graduation_assessment_profiles profile
                      JOIN requirement_datasets dataset
                        ON dataset.id = profile.dataset_id
                     WHERE (
                            profile.academic_unit_key = :academicUnitKey
                            OR profile.academic_unit_code = :academicUnitCode
                     )
                       AND profile.effective_year <=
                           EXTRACT(YEAR FROM CURRENT_DATE)::integer
                     ORDER BY profile.effective_year DESC,
                              dataset.imported_at DESC, profile.id
                     LIMIT 1
                )
                SELECT 'ASSESSMENT_' || category.category_code AS code,
                       category.category_name AS title,
                       concat_ws(' | ',
                           nullif(category.requirement_detail, ''),
                           nullif(category.reference_note, ''),
                           nullif(category.source_note, '')
                       ) AS description,
                       (
                           SELECT source.source_ref
                             FROM graduation_assessment_source_refs source
                            WHERE source.profile_id = category.profile_id
                            ORDER BY source."position"
                            LIMIT 1
                       ) AS source_ref
                  FROM graduation_assessment_categories category
                  JOIN selected_profile profile ON profile.id = category.profile_id
                 ORDER BY category.category_code
                """, parameters, (rs, rowNum) -> new ManualRule(
                rs.getString("code"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("source_ref"))));
        return result;
    }

    public List<CompletedCourse> findCompletedCourses(UUID userId) {
        return jdbcTemplate.query("""
                SELECT course_code, course_name, credits, category, area
                  FROM completed_courses
                 WHERE user_id = :userId
                   AND status = 'COMPLETED'
                 ORDER BY created_at, id
                """, Map.of("userId", userId), (rs, rowNum) -> new CompletedCourse(
                rs.getString("course_code"),
                rs.getString("course_name"),
                rs.getBigDecimal("credits"),
                rs.getString("category"),
                rs.getString("area")));
    }

    public Optional<String> findEvaluationSemester(String semesterId) {
        if (semesterId != null) {
            return jdbcTemplate.queryForList("""
                    SELECT id
                      FROM semesters
                     WHERE id = :semesterId
                    """, Map.of("semesterId", semesterId), String.class)
                    .stream()
                    .findFirst();
        }
        return jdbcTemplate.queryForList("""
                SELECT id
                  FROM semesters
                 WHERE is_active = true
                 ORDER BY prepared_at DESC, id DESC
                 LIMIT 1
                """, Map.of(), String.class)
                .stream()
                .findFirst();
    }

    public List<OfferedCourse> findOfferedCourses(
            UUID userId, String semesterId, String academicUnitCode) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("semesterId", semesterId)
                .addValue("academicUnitCode", academicUnitCode);
        return jdbcTemplate.query("""
                SELECT course.semester_id, course.course_code, course.name,
                       course.category, course.credits,
                       count(DISTINCT section.section_code) AS section_count,
                       coalesce(
                           bool_or(unit.academic_unit_code =
                               CAST(:academicUnitCode AS text)),
                           false
                       ) AS academic_unit_match
                  FROM courses course
                  JOIN sections section
                    ON section.semester_id = course.semester_id
                   AND section.course_code = course.course_code
                  LEFT JOIN section_academic_units unit
                    ON unit.semester_id = section.semester_id
                   AND unit.course_code = section.course_code
                   AND unit.section_code = section.section_code
                 WHERE course.semester_id = :semesterId
                   AND NOT EXISTS (
                       SELECT 1
                         FROM completed_courses completed
                        WHERE completed.user_id = :userId
                          AND completed.status IN (
                              'COMPLETED', 'IN_PROGRESS', 'PLANNED'
                          )
                          AND (
                              (
                                  completed.course_code IS NOT NULL
                                  AND lower(completed.course_code) =
                                      lower(course.course_code)
                              )
                              OR (
                                  completed.course_code IS NULL
                                  AND normalize_academic_unit_key(
                                      completed.course_name
                                  ) = normalize_academic_unit_key(course.name)
                              )
                          )
                   )
                 GROUP BY course.semester_id, course.course_code, course.name,
                          course.category, course.credits
                 ORDER BY course.name, course.course_code
                """, parameters, (rs, rowNum) -> new OfferedCourse(
                rs.getString("semester_id"),
                rs.getString("course_code"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getBigDecimal("credits"),
                rs.getInt("section_count"),
                rs.getBoolean("academic_unit_match")));
    }

    private MapSqlParameterSource scopeParameters(RuleScope scope) {
        return new MapSqlParameterSource()
                .addValue("admissionYear", scope.admissionYear())
                .addValue("academicUnit", scope.academicUnit())
                .addValue("studentType", scope.studentType())
                .addValue("programPath", scope.programPath());
    }

    public record RuleScope(
            int admissionYear,
            String academicUnit,
            String studentType,
            String programPath) {
    }

    public record StudentScope(
            Integer admissionYear,
            String academicUnitKey,
            String academicUnitName,
            String studentType,
            String programPath) {
    }

    public record RuleProfile(
            String id,
            String datasetId,
            String sourceRuleId,
            int admissionYear,
            String academicUnit,
            String academicUnitKey,
            String academicUnitCode,
            String studentType,
            String programPath,
            int totalCreditsMin,
            int majorFoundationMin,
            int majorRequiredMin,
            int majorElectiveMin,
            Integer additionalMajorMin,
            int primaryMajorMin,
            Integer secondaryProgramMin,
            boolean requiresManualReview,
            String liberalRequirementSetId,
            int liberalRequiredMin,
            int liberalElectiveMin,
            int liberalTotalMin,
            Integer liberalTotalMax,
            String datasetSourcePath) {
    }

    public record AreaRule(String area, int minCourses, Integer minCredits) {
    }

    public record RequiredCourseRule(
            String id,
            String requirementType,
            String courseCode,
            String courseName,
            List<String> acceptedNames,
            BigDecimal credits,
            Integer recommendedGrade,
            String sourceRef) {
    }

    public record RuleWarning(String code, int calculated, int printed) {
    }

    public record ManualRule(
            String code,
            String title,
            String description,
            String sourceRef) {
    }

    public record CompletedCourse(
            String courseCode,
            String courseName,
            BigDecimal credits,
            String category,
            String area) {
    }

    public record OfferedCourse(
            String semesterId,
            String courseCode,
            String courseName,
            String category,
            BigDecimal credits,
            int sectionCount,
            boolean academicUnitMatch) {
    }
}
