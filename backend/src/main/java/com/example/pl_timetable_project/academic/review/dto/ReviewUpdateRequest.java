package com.example.pl_timetable_project.academic.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "내 리뷰의 별점과 본문 수정 요청")
public record ReviewUpdateRequest(
        @Schema(description = "수정할 1~5점 정수 별점", example = "4")
        @Min(1)
        @Max(5)
        int rating,

        @Schema(description = "수정할 리뷰 본문", example = "수정한 리뷰 내용입니다.")
        @NotBlank
        String content) {
}
