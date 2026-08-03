package com.example.pl_timetable_project.user.repository;

import com.example.pl_timetable_project.user.dto.AcademicProgramResponse;
import com.example.pl_timetable_project.user.dto.AcademicProgramUpdateRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 학생별 가변 길이 전공 목록을 저장합니다. */
@Repository
public class StudentAcademicProgramRepository {
    private final JdbcTemplate jdbcTemplate;

    public StudentAcademicProgramRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AcademicProgramResponse> findActiveByUserId(UUID userId) {
        return jdbcTemplate.query("""
                SELECT program.id,
                       program.academic_unit_code,
                       unit.name AS academic_unit_name,
                       program.program_role,
                       program.status,
                       program.display_order
                  FROM student_academic_programs program
                  JOIN academic_units unit
                    ON unit.code = program.academic_unit_code
                 WHERE program.user_id = ?
                   AND program.status <> 'WITHDRAWN'
                 ORDER BY program.display_order, program.created_at, program.id
                """, (resultSet, rowNumber) -> new AcademicProgramResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("academic_unit_code"),
                resultSet.getString("academic_unit_name"),
                resultSet.getString("program_role"),
                resultSet.getString("status"),
                resultSet.getShort("display_order")), userId);
    }

    public void replaceActivePrograms(
            UUID userId, List<AcademicProgramUpdateRequest> programs) {
        jdbcTemplate.update(
                "DELETE FROM student_academic_programs WHERE user_id = ?",
                userId);
        for (int index = 0; index < programs.size(); index++) {
            AcademicProgramUpdateRequest program = programs.get(index);
            jdbcTemplate.update("""
                    INSERT INTO student_academic_programs (
                        user_id, academic_unit_code, program_role, status, display_order
                    ) VALUES (?, ?, ?, 'ACTIVE', ?)
                    """, userId, program.academicUnitCode(), program.role(), index);
        }
    }

    public void replacePrimary(UUID userId, String academicUnitCode) {
        List<AcademicProgramUpdateRequest> programs = findActiveByUserId(userId).stream()
                .filter(program -> !"PRIMARY".equals(program.role()))
                .map(program -> new AcademicProgramUpdateRequest(
                        program.academicUnitCode(), program.role()))
                .toList();
        java.util.ArrayList<AcademicProgramUpdateRequest> replacement =
                new java.util.ArrayList<>();
        replacement.add(new AcademicProgramUpdateRequest(academicUnitCode, "PRIMARY"));
        replacement.addAll(programs.stream()
                .filter(program -> !academicUnitCode.equals(program.academicUnitCode()))
                .toList());
        replaceActivePrograms(userId, replacement);
    }
}
