package com.example.pl_timetable_project.academic.review;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.review.dto.ReviewCreateRequest;
import com.example.pl_timetable_project.academic.review.dto.ReviewResponse;
import com.example.pl_timetable_project.academic.review.dto.ReviewUpdateRequest;
import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import com.example.pl_timetable_project.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
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
@Tag(name = "리뷰", description = "내 리뷰 작성·수정·삭제")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "리뷰 작성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> createReview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ReviewCreateRequest request) {
        return ApiResponse.success(reviewService.create(principal.userId(), request));
    }

    @Operation(summary = "내 리뷰 목록 조회")
    @GetMapping("/me")
    public ApiResponse<AcademicPageResponse<ReviewResponse>> listMyReviews(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String semesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageSpec.DEFAULT_SIZE) int size) {
        return ApiResponse.success(reviewService.listMine(
                principal.userId(), semesterId, page, size));
    }

    @Operation(summary = "내 리뷰 수정")
    @PatchMapping("/{reviewId}")
    public ApiResponse<ReviewResponse> updateReview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewUpdateRequest request) {
        return ApiResponse.success(reviewService.update(
                principal.userId(), reviewId, request));
    }

    @Operation(summary = "내 리뷰 삭제")
    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> deleteReview(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID reviewId) {
        reviewService.delete(principal.userId(), reviewId);
        return ApiResponse.success();
    }
}
