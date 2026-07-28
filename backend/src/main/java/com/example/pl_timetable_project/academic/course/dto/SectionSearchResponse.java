package com.example.pl_timetable_project.academic.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "메인 시간표 화면에서 검색·필터 후 표시하는 분반 카드")
public record SectionSearchResponse(
        String semesterId,
        String courseCode,
        String courseName,
        String sectionCode,
        String professor,
        String category,
        BigDecimal credits,
        BigDecimal lectureHours,
        BigDecimal practiceHours,
        String rawLectureTime,
        String rawLocation,
        boolean timeToBeAnnounced,
        String targetGrade,
        String completionCategory,
        Integer capacity,
        String notes,
        int warningCount,
        BigDecimal ratingAverage,
        long reviewCount,
        BigDecimal bayesianRating,
        List<CourseSessionResponse> sessions,
        List<SectionClassificationResponse> classifications) {
}
