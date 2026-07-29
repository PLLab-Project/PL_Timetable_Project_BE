package com.example.pl_timetable_project.optimization.entity;

import com.example.pl_timetable_project.optimization.algorithm.CourseTimeSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptimizationBlockedTime {

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 20, nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_minute", nullable = false)
    private Short startMinute;

    @Column(name = "end_minute", nullable = false)
    private Short endMinute;

    public OptimizationBlockedTime(CourseTimeSlot slot) {
        dayOfWeek = slot.dayOfWeek();
        startMinute = toMinute(slot.startTime());
        endMinute = toMinute(slot.endTime());
    }

    private static short toMinute(LocalTime time) {
        return (short) (time.getHour() * 60 + time.getMinute());
    }
}
