package com.example.pl_timetable_project.optimization.dto.response;

import com.example.pl_timetable_project.optimization.entity.CourseSlot;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record OptimizationCourseSlotResponse(
        String semesterId,
        String courseCode,
        String sectionCode,
        String courseName,
        String professorName,
        BigDecimal credits,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime) {

    public static OptimizationCourseSlotResponse from(CourseSlot slot) {
        return new OptimizationCourseSlotResponse(
                slot.getSection().getSemesterId(),
                slot.getSection().getCourseCode(),
                slot.getSection().getSectionCode(),
                slot.getCourseName(),
                slot.getProfessorName(),
                slot.getCredits(),
                slot.getDayOfWeek(),
                slot.getStartTime(),
                slot.getEndTime());
    }
}
