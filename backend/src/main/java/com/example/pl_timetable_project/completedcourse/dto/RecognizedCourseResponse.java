package com.example.pl_timetable_project.completedcourse.dto;

import com.example.pl_timetable_project.completedcourse.CompletedCourseGradingBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "성적표 OCR에서 구조화한 이수과목 후보. 사용자가 확인·수정한 뒤 등록합니다.")
public record RecognizedCourseResponse(
        @Schema(description = "인식한 과목명", example = "자료구조")
        String courseName,

        @Schema(description = "인식한 학점", example = "3.0")
        BigDecimal credits,

        @Schema(
                description = "성적 표기 방식",
                example = "LETTER",
                allowableValues = {"LETTER", "PASS_FAIL"})
        CompletedCourseGradingBasis gradingBasis,

        @Schema(description = "인식한 이수구분", example = "전공선택")
        String category,

        @Schema(description = "인식한 세부 영역. 확인할 수 없으면 null", example = "전공심화")
        String area,

        @Schema(description = "인식한 수강 학기. 확인할 수 없으면 null", example = "2026-1")
        String semester,

        @Schema(
                description = "구조화 결과의 인식 확신도(0~1)",
                example = "0.92",
                minimum = "0",
                maximum = "1")
        BigDecimal confidence) {
}
