package com.example.pl_timetable_project.academic.review;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.review.dto.ReviewResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses/reviews")
public class PublicReviewController {

    private final ReviewService reviewService;

    public PublicReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<AcademicPageResponse<ReviewResponse>> listReviews(
            @RequestParam(required = false) String semesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(reviewService.listPublic(
                semesterId, null, null, page, size));
    }

    @GetMapping("/{courseCode}")
    public ResponseEntity<AcademicPageResponse<ReviewResponse>> listCourseReviews(
            @PathVariable String courseCode,
            @RequestParam(required = false) String semesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(reviewService.listPublic(
                semesterId, courseCode, null, page, size));
    }

    @GetMapping("/{courseCode}/professors/{professor}")
    public ResponseEntity<AcademicPageResponse<ReviewResponse>> listProfessorReviews(
            @PathVariable String courseCode,
            @PathVariable String professor,
            @RequestParam(required = false) String semesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(reviewService.listPublic(
                semesterId, courseCode, professor, page, size));
    }
}
