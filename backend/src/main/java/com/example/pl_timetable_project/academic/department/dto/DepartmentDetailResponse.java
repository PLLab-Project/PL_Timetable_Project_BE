package com.example.pl_timetable_project.academic.department.dto;

import java.util.List;

public record DepartmentDetailResponse(
        String code,
        String name,
        String collegeCode,
        String collegeName,
        int firstSeenYear,
        int lastSeenYear,
        boolean current,
        List<DepartmentAliasResponse> aliases) {
}
