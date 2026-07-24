package com.example.pl_timetable_project.optimization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 편성 결과에 포함된 강의 한 요일-시간 슬롯의 스냅샷.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseSlot {

    private Long courseId;

    private String courseName;

    private String professorName;

    private Integer credit;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private DayOfWeek dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    public CourseSlot(Long courseId, String courseName, String professorName, Integer credit,
                       DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.professorName = professorName;
        this.credit = credit;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
