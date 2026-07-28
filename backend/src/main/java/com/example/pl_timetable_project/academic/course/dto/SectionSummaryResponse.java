package com.example.pl_timetable_project.academic.course.dto;

import java.util.List;

public record SectionSummaryResponse(
        String semesterId,
        String courseCode,
        String sectionCode,
        String professor,
        String rawLectureTime,
        String rawLocation,
        boolean timeToBeAnnounced,
        String targetGrade,
        Integer capacity,
        String notes,
        int warningCount,
        List<CourseSessionResponse> sessions) {
}
