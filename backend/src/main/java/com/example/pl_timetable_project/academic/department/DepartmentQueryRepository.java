package com.example.pl_timetable_project.academic.department;

import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.department.dto.DepartmentAliasResponse;
import com.example.pl_timetable_project.academic.department.dto.DepartmentDetailResponse;
import com.example.pl_timetable_project.academic.department.dto.DepartmentSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DepartmentQueryRepository {

    private static final String BASE_FILTER = """
            FROM academic_units u
            LEFT JOIN academic_colleges c ON c.code = u.college_code
            WHERE u.code_source = 'OFFICIAL_CURRICULUM'
              AND (CAST(:currentOnly AS boolean) = false OR u.is_current = true)
              AND (
                    CAST(:collegeCode AS text) IS NULL
                    OR u.college_code = CAST(:collegeCode AS text)
              )
              AND (
                    CAST(:query AS text) IS NULL
                    OR u.normalized_key LIKE '%'
                        || normalize_academic_unit_key(CAST(:query AS text)) || '%'
                    OR lower(u.code) LIKE '%'
                        || lower(CAST(:query AS text)) || '%'
              )
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DepartmentQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DepartmentSummaryResponse> findAll(
            String query,
            String collegeCode,
            boolean currentOnly,
            PageSpec pageSpec) {
        MapSqlParameterSource parameters = parameters(query, collegeCode, currentOnly)
                .addValue("limit", pageSpec.size())
                .addValue("offset", pageSpec.offset());
        return jdbcTemplate.query("""
                SELECT u.code, u.name, u.college_code, c.name AS college_name,
                       u.first_seen_year, u.last_seen_year, u.is_current
                """ + BASE_FILTER + """
                ORDER BY c.name NULLS LAST, u.name, u.code
                LIMIT :limit OFFSET :offset
                """, parameters, (rs, rowNum) -> new DepartmentSummaryResponse(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("college_code"),
                rs.getString("college_name"),
                rs.getInt("first_seen_year"),
                rs.getInt("last_seen_year"),
                rs.getBoolean("is_current")));
    }

    public long count(
            String query,
            String collegeCode,
            boolean currentOnly) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) " + BASE_FILTER,
                parameters(query, collegeCode, currentOnly),
                Long.class);
        return count == null ? 0 : count;
    }

    public Optional<DepartmentDetailResponse> findByCode(String code) {
        Optional<DepartmentRow> department = jdbcTemplate.query("""
                SELECT u.code, u.name, u.college_code, c.name AS college_name,
                       u.first_seen_year, u.last_seen_year, u.is_current
                  FROM academic_units u
                  LEFT JOIN academic_colleges c ON c.code = u.college_code
                 WHERE u.code = :code
                   AND u.code_source = 'OFFICIAL_CURRICULUM'
                """, Map.of("code", code), (rs, rowNum) -> new DepartmentRow(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("college_code"),
                rs.getString("college_name"),
                rs.getInt("first_seen_year"),
                rs.getInt("last_seen_year"),
                rs.getBoolean("is_current")))
                .stream()
                .findFirst();
        return department.map(row -> new DepartmentDetailResponse(
                row.code(),
                row.name(),
                row.collegeCode(),
                row.collegeName(),
                row.firstSeenYear(),
                row.lastSeenYear(),
                row.current(),
                findAliases(code)));
    }

    private List<DepartmentAliasResponse> findAliases(String code) {
        return jdbcTemplate.query("""
                SELECT alias, valid_from_year, valid_to_year, source_kind, is_primary
                  FROM academic_unit_aliases
                 WHERE academic_unit_code = :code
                 ORDER BY is_primary DESC, valid_from_year NULLS FIRST, alias
                """, Map.of("code", code), (rs, rowNum) -> new DepartmentAliasResponse(
                rs.getString("alias"),
                rs.getObject("valid_from_year", Integer.class),
                rs.getObject("valid_to_year", Integer.class),
                rs.getString("source_kind"),
                rs.getBoolean("is_primary")));
    }

    private MapSqlParameterSource parameters(
            String query, String collegeCode, boolean currentOnly) {
        return new MapSqlParameterSource()
                .addValue("query", query)
                .addValue("collegeCode", collegeCode)
                .addValue("currentOnly", currentOnly);
    }

    private record DepartmentRow(
            String code,
            String name,
            String collegeCode,
            String collegeName,
            int firstSeenYear,
            int lastSeenYear,
            boolean current) {
    }
}
