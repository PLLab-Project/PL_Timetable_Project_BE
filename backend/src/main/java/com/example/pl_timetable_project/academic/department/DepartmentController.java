package com.example.pl_timetable_project.academic.department;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.department.dto.DepartmentDetailResponse;
import com.example.pl_timetable_project.academic.department.dto.DepartmentSummaryResponse;
import com.example.pl_timetable_project.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "학과", description = "정규화된 대학·학과·전공 조회")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @Operation(summary = "학과·전공 목록 조회")
    @GetMapping
    public ApiResponse<AcademicPageResponse<DepartmentSummaryResponse>> getDepartments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String collegeCode,
            @RequestParam(defaultValue = "true") boolean currentOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ApiResponse.success(departmentService.getDepartments(
                query, collegeCode, currentOnly, page, size));
    }

    @Operation(summary = "학과·전공 상세 조회")
    @GetMapping("/{code}")
    public ApiResponse<DepartmentDetailResponse> getDepartment(
            @PathVariable String code) {
        return ApiResponse.success(departmentService.getDepartment(code));
    }
}
