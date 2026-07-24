package com.example.pl_timetable_project.academic.review;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.review.dto.ReviewCreateRequest;
import com.example.pl_timetable_project.academic.review.dto.ReviewResponse;
import com.example.pl_timetable_project.academic.review.dto.ReviewUpdateRequest;
import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ReviewCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(principal.userId(), request));
    }

    @GetMapping("/me")
    public ResponseEntity<AcademicPageResponse<ReviewResponse>> listMyReviews(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String semesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(reviewService.listMine(
                principal.userId(), semesterId, page, size));
    }

    @PatchMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> updateReview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequest request) {
        return ResponseEntity.ok(reviewService.update(
                principal.userId(), reviewId, request));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reviewId) {
        reviewService.delete(principal.userId(), reviewId);
        return ResponseEntity.noContent().build();
    }
}
