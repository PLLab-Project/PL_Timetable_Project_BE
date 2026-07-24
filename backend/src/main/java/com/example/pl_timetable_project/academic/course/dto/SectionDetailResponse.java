package com.example.pl_timetable_project.academic.course.dto;

import java.util.List;

public record SectionDetailResponse(
        String semesterId,
        String courseCode,
        String sectionCode,
        String professor,
        String rawLectureTime,
        boolean timeToBeAnnounced,
        List<String> warningCodes,
        List<CourseSessionResponse> sessions,
        List<CourseAcademicUnitResponse> academicUnits) {
}
