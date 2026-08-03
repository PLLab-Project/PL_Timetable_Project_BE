package com.example.pl_timetable_project.completedcourse.dto;

import com.example.pl_timetable_project.completedcourse.CompletedCourseGradingBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "이미지에서 구조화하고 실제 강의 DB와 비교한 과목 후보")
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
        BigDecimal confidence,

        @Schema(description = "이미지에서 인식한 담당 교수. 보이지 않으면 null", example = "박정규")
        String professor,

        @Schema(description = "이미지에서 인식한 요일·시간·강의실 목록")
        List<RecognizedCourseMeetingResponse> meetings,

        @Schema(description = "실제 강의 DB 분반 매칭 상태", example = "MATCHED")
        OcrCourseMatchStatus matchStatus,

        @Schema(description = "유일하게 확정된 실제 분반. 확정되지 않으면 null")
        OcrSectionMatchCandidateResponse matchedSection,

        @Schema(description = "점수순 실제 분반 후보. 최대 5개")
        List<OcrSectionMatchCandidateResponse> matchCandidates) {

    public RecognizedCourseResponse withMatching(
            OcrCourseMatchStatus status,
            OcrSectionMatchCandidateResponse matched,
            List<OcrSectionMatchCandidateResponse> candidates) {
        OcrSectionMatchCandidateResponse canonical = matched;
        if (canonical == null
                && status == OcrCourseMatchStatus.COURSE_MATCHED
                && candidates != null
                && !candidates.isEmpty()) {
            canonical = candidates.get(0);
        }
        String canonicalCategory = canonical == null
                ? category
                : canonicalCategory(canonical, category);
        return new RecognizedCourseResponse(
                canonical != null && canonical.courseName() != null
                        ? canonical.courseName() : courseName,
                canonical != null && canonical.credits() != null
                        ? canonical.credits() : credits,
                gradingBasis,
                canonicalCategory,
                canonical != null && area == null ? canonical.category() : area,
                canonical != null && canonical.semesterId() != null
                        ? canonical.semesterId() : semester,
                confidence,
                professor,
                meetings,
                status,
                matched,
                candidates);
    }

    private static String canonicalCategory(
            OcrSectionMatchCandidateResponse canonical,
            String recognizedCategory) {
        String completionCategory = canonical.completionCategory();
        String catalogCategory = canonical.category();
        if (catalogCategory != null && catalogCategory.contains("제")
                && catalogCategory.contains("영역")) {
            return catalogCategory;
        }
        if (completionCategory == null || completionCategory.isBlank()) {
            return catalogCategory != null ? catalogCategory : recognizedCategory;
        }
        return switch (completionCategory.replaceAll("\\s", "")) {
            case "전필", "전공필수" -> "전공필수";
            case "전선", "전공선택" -> "전공선택";
            case "교필", "교양필수" -> "교양필수";
            case "교선", "교양선택" -> "교양선택";
            case "일선", "일반선택" -> "일반선택";
            default -> completionCategory;
        };
    }
}
