package com.example.pl_timetable_project.completedcourse.dto;

import com.example.pl_timetable_project.academic.course.dto.CourseSessionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "OCR 인식 결과와 실제 강의 DB를 비교한 분반 후보")
public record OcrSectionMatchCandidateResponse(
        @Schema(description = "학기 ID", example = "2026-1")
        String semesterId,

        @Schema(description = "과목 코드", example = "561103")
        String courseCode,

        @Schema(description = "분반 코드", example = "01")
        String sectionCode,

        @Schema(description = "DB 과목명", example = "자료구조")
        String courseName,

        @Schema(description = "담당 교수", example = "박정규")
        String professor,

        @Schema(description = "DB 이수구분", example = "전공(AI융합대학/컴퓨터공학전공)")
        String category,

        @Schema(
                description = "사용자 학과 문맥을 우선한 이수구분",
                example = "전선",
                nullable = true)
        String completionCategory,

        @Schema(description = "DB 학점", example = "3.0")
        BigDecimal credits,

        @Schema(description = "DB 원문 강의시간", example = "월15:30-17:30,화11:30-13:30")
        String rawLectureTime,

        @Schema(description = "DB 원문 강의실")
        String rawLocation,

        @Schema(description = "DB 정규화 수업 세션")
        List<CourseSessionResponse> sessions,

        @Schema(description = "이 후보의 결정적 매칭 점수(0~1)", example = "1.00")
        BigDecimal matchScore,

        @Schema(description = "점수 산정에 사용한 일치 근거")
        List<String> matchedEvidence) {
}
