package com.example.pl_timetable_project.completedcourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "성적표·시간표 이미지 OCR 및 실제 강의 DB 매칭 결과")
public record CompletedCourseOcrResponse(
        @Schema(description = "OCR 공급자", example = "GEMINI_3_5_FLASH_LITE")
        String provider,

        @Schema(description = "문서 전체 인식 텍스트")
        String extractedText,

        @Schema(description = "빈 줄을 제거한 인식 행 목록")
        List<String> lines,

        @Schema(description = "Gemini가 판별한 이미지 유형", example = "TIMETABLE")
        OcrDocumentType documentType,

        @Schema(
                description = "Gemini가 이미지에서 직접 확인한 학기. 보이지 않으면 null",
                example = "2026-1",
                nullable = true)
        String recognizedSemester,

        @Schema(
                description = "이미지 인식값 또는 실제 강의 DB 매칭으로 확정한 학기. 확정할 수 없으면 null",
                example = "2026-1",
                nullable = true)
        String resolvedSemester,

        @Schema(description = "이수과목 등록 API 필드에 맞춰 구조화한 과목 후보 목록")
        List<RecognizedCourseResponse> recognizedCourses,

        @Schema(description = "항상 true. OCR 결과는 자동 저장하지 않고 사용자 확인이 필요합니다.")
        boolean requiresConfirmation) {
}
