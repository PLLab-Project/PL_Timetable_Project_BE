package com.example.pl_timetable_project.optimization.dto.response;

import com.example.pl_timetable_project.optimization.entity.CourseSlot;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Getter;

@Getter
public class OptimizationCourseSlotResponse {

    private final Long courseId;
    private final String courseName;
    private final String professorName;
    private final Integer credit;
    private final DayOfWeek dayOfWeek;
    private final LocalTime startTime;
    private final LocalTime endTime;

    private OptimizationCourseSlotResponse(Long courseId, String courseName, String professorName, Integer credit,
                                            DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.professorName = professorName;
        this.credit = credit;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static OptimizationCourseSlotResponse from(CourseSlot slot) {
        return new OptimizationCourseSlotResponse(
                slot.getCourseId(),
                slot.getCourseName(),
                slot.getProfessorName(),
                slot.getCredit(),
                slot.getDayOfWeek(),
                slot.getStartTime(),
                slot.getEndTime());
    }
}
