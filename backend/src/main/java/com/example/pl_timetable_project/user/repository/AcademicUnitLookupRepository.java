package com.example.pl_timetable_project.user.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 사용자 프로필이 참조하는 정규 학과의 코드와 표시 이름을 조회합니다. */
@Repository
public class AcademicUnitLookupRepository {
    private final JdbcTemplate jdbcTemplate;

    public AcademicUnitLookupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AcademicUnit> findCurrentByCode(String code) {
        return jdbcTemplate.query(
                """
                SELECT code, name
                  FROM academic_units
                 WHERE code = ?
                   AND is_current = true
                """,
                (resultSet, rowNumber) -> new AcademicUnit(
                        resultSet.getString("code"),
                        resultSet.getString("name")),
                code
        ).stream().findFirst();
    }

    public Optional<AcademicUnit> findByCode(String code) {
        return jdbcTemplate.query(
                "SELECT code, name FROM academic_units WHERE code = ?",
                (resultSet, rowNumber) -> new AcademicUnit(
                        resultSet.getString("code"),
                        resultSet.getString("name")),
                code
        ).stream().findFirst();
    }

    public record AcademicUnit(String code, String name) {
    }
}
