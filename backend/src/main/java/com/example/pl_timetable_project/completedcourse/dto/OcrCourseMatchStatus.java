package com.example.pl_timetable_project.completedcourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OCR 과목과 실제 강의 분반의 매칭 상태")
public enum OcrCourseMatchStatus {
    MATCHED,
    COURSE_MATCHED,
    AMBIGUOUS,
    UNMATCHED
}
