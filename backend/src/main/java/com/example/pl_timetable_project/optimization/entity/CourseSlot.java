package com.example.pl_timetable_project.optimization.entity;

import com.example.pl_timetable_project.academic.section.SectionReference;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseSlot {

    @Embedded
    private SectionReference section;

    @Column(name = "course_name", nullable = false)
    private String courseName;

    @Column(name = "professor_name")
    private String professorName;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal credits;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 20, nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_minute", nullable = false)
    private Short startMinute;

    @Column(name = "end_minute", nullable = false)
    private Short endMinute;

    public CourseSlot(
            SectionReference section,
            String courseName,
            String professorName,
            BigDecimal credits,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime) {
        this.section = section;
        this.courseName = courseName;
        this.professorName = professorName;
        this.credits = credits;
        this.dayOfWeek = dayOfWeek;
        this.startMinute = toMinute(startTime);
        this.endMinute = toMinute(endTime);
    }

    public LocalTime getStartTime() {
        return LocalTime.ofSecondOfDay(startMinute * 60L);
    }

    public LocalTime getEndTime() {
        return LocalTime.ofSecondOfDay(endMinute * 60L);
    }

    private static short toMinute(LocalTime time) {
        return (short) (time.getHour() * 60 + time.getMinute());
    }
}
