package com.example.pl_timetable_project.academic.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "실제 개설 강의에 대한 리뷰 작성 요청")
public record ReviewCreateRequest(
        @Schema(description = "리뷰 대상 학기 ID", example = "2026-1")
        @NotBlank
        @Size(max = 20)
        String semesterId,

        @Schema(description = "리뷰 대상 과목코드", example = "855121")
        @NotBlank
        @Size(max = 40)
        String courseCode,

        @Schema(
                description = "리뷰 대상 교수명. 교수 정보가 없는 강의는 null 가능",
                example = "홍교수")
        @Size(max = 120)
        String professor,

        @Schema(description = "1점부터 5점까지의 정수 별점", example = "5")
        @Min(1)
        @Max(5)
        int rating,

        @Schema(description = "리뷰 본문", example = "설명이 명확하고 실습이 유익했습니다.")
        @NotBlank
        String content) {
}
