package com.example.pl_timetable_project.academic.course;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.course.dto.CourseDetailResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseSummaryResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionDetailResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
@Tag(name = "강의", description = "학기별 강의 검색과 분반 조회")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @Operation(summary = "강의 검색·필터·정렬")
    @GetMapping
    public ResponseEntity<AcademicPageResponse<CourseSummaryResponse>> searchCourses(
            @RequestParam String semesterId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String academicUnitCode,
            @RequestParam(required = false) String professor,
            @RequestParam(required = false) BigDecimal credits,
            @RequestParam(required = false) String day,
            @RequestParam(defaultValue = "NAME_ASC") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(courseService.searchCourses(
                semesterId,
                query,
                category,
                academicUnitCode,
                professor,
                credits,
                day,
                sort,
                page,
                size));
    }

    @Operation(summary = "강의 상세 조회")
    @GetMapping("/{semesterId}/{courseCode}")
    public ResponseEntity<CourseDetailResponse> getCourse(
            @PathVariable String semesterId,
            @PathVariable String courseCode) {
        return ResponseEntity.ok(courseService.getCourse(semesterId, courseCode));
    }

    @Operation(summary = "강의의 분반 목록 조회")
    @GetMapping("/{semesterId}/{courseCode}/sections")
    public ResponseEntity<List<SectionSummaryResponse>> getSections(
            @PathVariable String semesterId,
            @PathVariable String courseCode) {
        return ResponseEntity.ok(courseService.getSections(semesterId, courseCode));
    }

    @Operation(summary = "분반 상세 조회")
    @GetMapping("/{semesterId}/{courseCode}/sections/{sectionCode}")
    public ResponseEntity<SectionDetailResponse> getSection(
            @PathVariable String semesterId,
            @PathVariable String courseCode,
            @PathVariable String sectionCode) {
        return ResponseEntity.ok(courseService.getSection(
                semesterId, courseCode, sectionCode));
    }
}
