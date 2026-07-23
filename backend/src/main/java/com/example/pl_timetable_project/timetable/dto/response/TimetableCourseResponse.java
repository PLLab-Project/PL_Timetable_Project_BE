package com.example.pl_timetable_project.timetable.dto.response;

import com.example.pl_timetable_project.timetable.entity.TimetableCourse;
import java.math.BigDecimal;
import java.util.List;

public record TimetableCourseResponse(
        Long id,
        String semesterId,
        String courseCode,
        String sectionCode,
        String courseName,
        String professorName,
        BigDecimal credits,
        List<TimetableMeetingResponse> meetings) {

    public static TimetableCourseResponse from(TimetableCourse course) {
        return new TimetableCourseResponse(
                course.getId(),
                course.getSection().getSemesterId(),
                course.getSection().getCourseCode(),
                course.getSection().getSectionCode(),
                course.getCourseName(),
                course.getProfessorName(),
                course.getCredits(),
                course.getMeetings().stream().map(TimetableMeetingResponse::from).toList());
    }
}
