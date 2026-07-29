package com.example.pl_timetable_project.academic.course;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.course.dto.SectionSearchResponse;
import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import com.example.pl_timetable_project.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sections")
@Tag(name = "강의", description = "학기별 강의 검색과 분반 조회")
public class SectionSearchController {

    private final CourseService courseService;

    public SectionSearchController(CourseService courseService) {
        this.courseService = courseService;
    }

    @Operation(summary = "메인 화면 분반 검색·필터·정렬")
    @GetMapping
    public ApiResponse<AcademicPageResponse<SectionSearchResponse>> searchSections(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam String semesterId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) List<String> academicUnitCode,
            @RequestParam(required = false) List<String> collegeCode,
            @RequestParam(required = false) List<String> completionCategory,
            @RequestParam(required = false) List<String> targetGrade,
            @RequestParam(required = false) String preferredAcademicUnitCode,
            @RequestParam(required = false) String professor,
            @RequestParam(required = false) BigDecimal credits,
            @RequestParam(required = false) String day,
            @RequestParam(defaultValue = "DEFAULT") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ApiResponse.success(courseService.searchSections(
                principal == null ? null : principal.userId(),
                semesterId,
                query,
                category,
                academicUnitCode,
                collegeCode,
                completionCategory,
                targetGrade,
                preferredAcademicUnitCode,
                professor,
                credits,
                day,
                sort,
                page,
                size));
    }
}
