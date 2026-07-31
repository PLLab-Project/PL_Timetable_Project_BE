package com.example.pl_timetable_project.completedcourse.dto;

import com.example.pl_timetable_project.completedcourse.CompletedCourseGradingBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "OCR 텍스트에서 추출을 시도한 과목 한 건. 필드별로 인식하지 못하면 null입니다.")
public record RecognizedCourse(
        @Schema(description = "과목명", example = "1인미디어제작실습")
        String courseName,

        @Schema(description = "취득 학점", example = "2.0")
        BigDecimal credits,

        @Schema(
                description = "성적 표기 방식",
                example = "PASS_FAIL",
                allowableValues = {"LETTER", "PASS_FAIL"})
        CompletedCourseGradingBasis gradingBasis,

        @Schema(description = "전공필수·전공선택·교양 등 이수구분", example = "전공선택")
        String category,

        @Schema(description = "전공심화·교양영역 등 세부 영역", example = "전공심화")
        String area,

        @Schema(description = "수강한 학기", example = "2026-1")
        String semester,

        @Schema(description = "이 항목 인식에 대한 모델의 확신도(0~1). 알 수 없으면 null", example = "0.82")
        Double confidence) {
}
