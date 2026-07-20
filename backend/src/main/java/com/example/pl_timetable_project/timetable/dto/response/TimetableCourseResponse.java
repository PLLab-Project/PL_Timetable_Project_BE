package com.example.pl_timetable_project.timetable.dto.response;

import com.example.pl_timetable_project.timetable.entity.TimetableCourse;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Getter;

@Getter
public class TimetableCourseResponse {

    private final Long id;
    private final Long courseId;
    private final String courseName;
    private final String professorName;
    private final Integer credit;
    private final DayOfWeek dayOfWeek;
    private final LocalTime startTime;
    private final LocalTime endTime;

    private TimetableCourseResponse(Long id, Long courseId, String courseName, String professorName,
                                     Integer credit, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.id = id;
        this.courseId = courseId;
        this.courseName = courseName;
        this.professorName = professorName;
        this.credit = credit;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static TimetableCourseResponse from(TimetableCourse course) {
        return new TimetableCourseResponse(
                course.getId(),
                course.getCourseId(),
                course.getCourseName(),
                course.getProfessorName(),
                course.getCredit(),
                course.getDayOfWeek(),
                course.getStartTime(),
                course.getEndTime());
    }
}
