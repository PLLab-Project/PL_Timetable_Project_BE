package com.example.pl_timetable_project.completedcourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "성적표 이미지 OCR 결과. 사용자가 확인·보정한 뒤 이수과목을 저장해야 합니다.")
public record CompletedCourseOcrResponse(
        @Schema(description = "OCR 공급자", example = "GEMINI_3_5_FLASH_LITE")
        String provider,

        @Schema(description = "문서 전체 인식 텍스트")
        String extractedText,

        @Schema(description = "빈 줄을 제거한 인식 행 목록")
        List<String> lines,

        @Schema(description = "전사된 텍스트에서 추출을 시도한 과목 목록. 인식하지 못하면 빈 배열이며, "
                + "이 경우 extractedText/lines를 참고해 사용자가 직접 입력해야 합니다.")
        List<RecognizedCourse> recognizedCourses,

        @Schema(description = "항상 true. OCR 결과는 자동 저장하지 않고 사용자 확인이 필요합니다.")
        boolean requiresConfirmation) {
}
