package com.example.pl_timetable_project.academic.course.dto;

import java.util.List;

public record SectionSummaryResponse(
        String semesterId,
        String courseCode,
        String sectionCode,
        String professor,
        String rawLectureTime,
        boolean timeToBeAnnounced,
        int warningCount,
        List<CourseSessionResponse> sessions) {
}
