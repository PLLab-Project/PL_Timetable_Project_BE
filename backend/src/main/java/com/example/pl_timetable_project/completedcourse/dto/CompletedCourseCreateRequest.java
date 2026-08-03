package com.example.pl_timetable_project.completedcourse.dto;

import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import com.example.pl_timetable_project.completedcourse.CompletedCourseGradingBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "이수·수강 중·계획 과목 직접 등록 요청")
public record CompletedCourseCreateRequest(
        @Schema(
                description = "학교 과목코드. 직접 입력 과목이라 코드가 없으면 null 가능",
                example = "855121")
        @Size(max = 40)
        String courseCode,

        @Schema(description = "성적표 또는 교육과정에 표시된 과목명", example = "1인미디어제작실습")
        @NotBlank
        @Size(max = 240)
        String courseName,

        @Schema(description = "취득 또는 신청 학점. 음수 불가, 소수 둘째 자리까지", example = "2.0")
        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal credits,

        @Schema(
                description = "성적 표기 방식. P/N 과목도 credits에는 실제 인정 학점을 입력",
                example = "PASS_FAIL",
                allowableValues = {"LETTER", "PASS_FAIL"})
        CompletedCourseGradingBasis gradingBasis,

        @Schema(description = "성적 표기. PASS_FAIL이면 P 또는 N", example = "P")
        @Size(max = 12)
        String gradeValue,

        @Schema(description = "전공필수·전공선택·교양 등 이수구분", example = "전공선택")
        @NotBlank
        @Size(max = 160)
        String category,

        @Schema(description = "전공심화·교양영역 등 세부 영역. 해당 없으면 null", example = "전공심화")
        @Size(max = 120)
        String area,

        @Schema(description = "수강한 학기. 확인할 수 없으면 null", example = "2026-1")
        @Size(max = 20)
        String semester,

        @Schema(
                description = "수강 상태",
                example = "IN_PROGRESS",
                allowableValues = {"COMPLETED", "IN_PROGRESS", "PLANNED", "FAILED", "WITHDRAWN"})
        @NotNull
        CompletedCourseStatus status,

        @Schema(
                description = "OCR 후보에서 선택한 과거 강의 개설 이력 ID. 지정하면 과목·분반 정보를 DB 정규값으로 저장",
                example = "2020-1-927313-01",
                nullable = true)
        @Size(max = 36)
        String historicalOfferingId) {
}
