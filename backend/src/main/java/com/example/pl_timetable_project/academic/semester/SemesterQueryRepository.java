package com.example.pl_timetable_project.academic.semester;

import com.example.pl_timetable_project.academic.semester.dto.SemesterDataVersionResponse;
import com.example.pl_timetable_project.academic.semester.dto.SemesterResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SemesterQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SemesterQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SemesterResponse> findAll(boolean activeOnly) {
        return jdbcTemplate.query("""
                SELECT id, prepared_at, dataset_version, is_active, created_at
                  FROM semesters
                 WHERE (:activeOnly = false OR is_active = true)
                 ORDER BY prepared_at DESC, id DESC
                """, Map.of("activeOnly", activeOnly), (rs, rowNum) -> new SemesterResponse(
                rs.getString("id"),
                rs.getObject("prepared_at", java.time.LocalDate.class),
                rs.getString("dataset_version"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant()));
    }

    public Optional<SemesterResponse> findById(String semesterId) {
        return jdbcTemplate.query("""
                SELECT id, prepared_at, dataset_version, is_active, created_at
                  FROM semesters
                 WHERE id = :semesterId
                """, Map.of("semesterId", semesterId), (rs, rowNum) -> new SemesterResponse(
                rs.getString("id"),
                rs.getObject("prepared_at", java.time.LocalDate.class),
                rs.getString("dataset_version"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").toInstant()))
                .stream()
                .findFirst();
    }

    public Optional<SemesterDataVersionResponse> findDataVersion(String semesterId) {
        return jdbcTemplate.query("""
                SELECT id, dataset_version, source_checksum, prepared_at, created_at
                  FROM semesters
                 WHERE id = :semesterId
                """, Map.of("semesterId", semesterId),
                (rs, rowNum) -> new SemesterDataVersionResponse(
                        rs.getString("id"),
                        rs.getString("dataset_version"),
                        rs.getString("source_checksum"),
                        rs.getObject("prepared_at", java.time.LocalDate.class),
                        rs.getTimestamp("created_at").toInstant()))
                .stream()
                .findFirst();
    }
}
