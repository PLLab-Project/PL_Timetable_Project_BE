package com.example.pl_timetable_project.user.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 학과 원본 도메인은 수정하지 않고, 회원 수정에 필요한 이름만 읽습니다. */
@Repository
public class AcademicUnitLookupRepository {
    private final JdbcTemplate jdbcTemplate;

    public AcademicUnitLookupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findCurrentNameByCode(String code) {
        return jdbcTemplate.query(
                "select name from academic_units where code = ? and is_current = true",
                (resultSet, rowNumber) -> resultSet.getString("name"), code
        ).stream().findFirst();
    }
}
