package com.example.pl_timetable_project.academic.review;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.review.dto.ReviewResponse;
import com.example.pl_timetable_project.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/reviews")
@Tag(name = "리뷰", description = "공개 강의·교수 리뷰 조회")
public class PublicReviewController {

    private final ReviewService reviewService;

    public PublicReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "전체 공개 리뷰 조회")
    @GetMapping
    public ApiResponse<AcademicPageResponse<ReviewResponse>> listReviews(
            @RequestParam(required = false) String semesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ApiResponse.success(reviewService.listPublic(
                semesterId, null, null, page, size));
    }

    @Operation(summary = "과목별 공개 리뷰 조회")
    @GetMapping("/{courseCode}")
    public ApiResponse<AcademicPageResponse<ReviewResponse>> listCourseReviews(
            @PathVariable String courseCode,
            @RequestParam(required = false) String semesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ApiResponse.success(reviewService.listPublic(
                semesterId, courseCode, null, page, size));
    }

    @Operation(summary = "과목·교수별 공개 리뷰 조회")
    @GetMapping("/{courseCode}/professors/{professor}")
    public ApiResponse<AcademicPageResponse<ReviewResponse>> listProfessorReviews(
            @PathVariable String courseCode,
            @PathVariable String professor,
            @RequestParam(required = false) String semesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ApiResponse.success(reviewService.listPublic(
                semesterId, courseCode, professor, page, size));
    }
}
