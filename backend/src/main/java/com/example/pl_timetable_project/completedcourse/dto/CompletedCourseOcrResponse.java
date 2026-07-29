package com.example.pl_timetable_project.completedcourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "성적표 이미지 OCR 결과. 사용자가 확인·보정한 뒤 이수과목을 저장해야 합니다.")
public record CompletedCourseOcrResponse(
        @Schema(description = "OCR 공급자", example = "GOOGLE_CLOUD_VISION")
        String provider,

        @Schema(description = "문서 전체 인식 텍스트")
        String extractedText,

        @Schema(description = "빈 줄을 제거한 인식 행 목록")
        List<String> lines,

        @Schema(description = "항상 true. OCR 결과는 자동 저장하지 않고 사용자 확인이 필요합니다.")
        boolean requiresConfirmation) {
}
