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

    /**
     * 자동편성 가중치·수강제한 판정에 "본인 학과"로 취급할 학과 코드 목록이다.
     * MINOR/MICRO_MAJOR는 제외한다 — 부전공·마이크로전공은 정식 학위 전공이 아니라
     * 보통 그 학과 강의 전체가 아닌 소수 지정 과목만 이수하면 되고, "OO학과만신청가능"류
     * 수강제한도 실제로는 정규/복수전공생에게만 열리는 경우가 대부분이라 여기 포함시키면
     * 자동편성 가중치·제외 판정이 지나치게 넓어진다.
     */
    private static final List<String> ACADEMIC_UNIT_MEMBERSHIP_ROLES =
            List.of("PRIMARY", "DOUBLE_MAJOR");

    public List<String> findMajorAcademicUnitCodes(UUID userId) {
        return jdbcTemplate.query("""
                SELECT DISTINCT program.academic_unit_code
                  FROM student_academic_programs program
                 WHERE program.user_id = ?
                   AND program.status <> 'WITHDRAWN'
                   AND program.program_role IN (?, ?)
                """,
                (resultSet, rowNumber) -> resultSet.getString("academic_unit_code"),
                userId,
                ACADEMIC_UNIT_MEMBERSHIP_ROLES.get(0),
                ACADEMIC_UNIT_MEMBERSHIP_ROLES.get(1));
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
