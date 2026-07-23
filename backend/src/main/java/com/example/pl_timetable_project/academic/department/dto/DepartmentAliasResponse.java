package com.example.pl_timetable_project.academic.department.dto;

public record DepartmentAliasResponse(
        String alias,
        Integer validFromYear,
        Integer validToYear,
        String sourceKind,
        boolean primary) {
}
