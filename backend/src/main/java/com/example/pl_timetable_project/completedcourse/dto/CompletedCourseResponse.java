package com.example.pl_timetable_project.completedcourse.dto;

import com.example.pl_timetable_project.completedcourse.CompletedCourseInputSource;
import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import com.example.pl_timetable_project.completedcourse.entity.CompletedCourse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CompletedCourseResponse(
        UUID id,
        String courseCode,
        String courseName,
        BigDecimal credits,
        String category,
        String area,
        String semester,
        CompletedCourseStatus status,
        String historicalOfferingId,
        String sectionCode,
        CompletedCourseInputSource inputSource,
        Map<String, Object> sourceSnapshot,
        Instant createdAt,
        Instant updatedAt) {

    public static CompletedCourseResponse from(CompletedCourse course) {
        return new CompletedCourseResponse(
                course.getId(),
                course.getCourseCode(),
                course.getCourseName(),
                course.getCredits(),
                course.getCategory(),
                course.getArea(),
                course.getSemester(),
                course.getStatus(),
                course.getHistoricalOfferingId(),
                course.getSectionCode(),
                course.getInputSource(),
                course.getSourceSnapshot(),
                course.getCreatedAt(),
                course.getUpdatedAt());
    }
}
