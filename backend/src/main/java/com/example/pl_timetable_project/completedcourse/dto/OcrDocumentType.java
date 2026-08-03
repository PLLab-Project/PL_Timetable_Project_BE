package com.example.pl_timetable_project.completedcourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Gemini가 판별한 학사 이미지 유형")
public enum OcrDocumentType {
    TIMETABLE,
    TRANSCRIPT,
    OTHER
}
