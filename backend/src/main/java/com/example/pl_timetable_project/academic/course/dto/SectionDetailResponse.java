package com.example.pl_timetable_project.academic.course.dto;

import java.util.List;

public record SectionDetailResponse(
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
        Integer sourcePage,
        Integer sourceRow,
        List<String> warningCodes,
        List<CourseSessionResponse> sessions,
        List<CourseAcademicUnitResponse> academicUnits,
        List<SectionClassificationResponse> classifications) {
}
