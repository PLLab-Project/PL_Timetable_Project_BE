package com.example.pl_timetable_project.academic.department;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.common.TextQuery;
import com.example.pl_timetable_project.academic.department.dto.DepartmentDetailResponse;
import com.example.pl_timetable_project.academic.department.dto.DepartmentSummaryResponse;
import com.example.pl_timetable_project.exception.AcademicResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentQueryRepository repository;

    public DepartmentService(DepartmentQueryRepository repository) {
        this.repository = repository;
    }

    public AcademicPageResponse<DepartmentSummaryResponse> getDepartments(
            String query,
            String collegeCode,
            boolean currentOnly,
            int page,
            int size) {
        PageSpec pageSpec = PageSpec.of(page, size);
        String normalizedQuery = TextQuery.optional(query);
        String normalizedCollegeCode = TextQuery.optional(collegeCode);
        return AcademicPageResponse.of(
                repository.findAll(
                        normalizedQuery, normalizedCollegeCode, currentOnly, pageSpec),
                pageSpec,
                repository.count(normalizedQuery, normalizedCollegeCode, currentOnly));
    }

    public DepartmentDetailResponse getDepartment(String code) {
        String normalizedCode = TextQuery.required(code, "학과 코드");
        return repository.findByCode(normalizedCode)
                .orElseThrow(() -> new AcademicResourceNotFoundException(
                        "학과를 찾을 수 없습니다. code=" + normalizedCode));
    }
}
