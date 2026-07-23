package com.example.pl_timetable_project.timetable.entity;

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
public class TimetableMeeting {

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 20, nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_minute", nullable = false)
    private Short startMinute;

    @Column(name = "end_minute", nullable = false)
    private Short endMinute;

    public TimetableMeeting(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
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

    public boolean overlaps(TimetableMeeting other) {
        return dayOfWeek == other.dayOfWeek
                && startMinute < other.endMinute
                && other.startMinute < endMinute;
    }

    private static short toMinute(LocalTime time) {
        return (short) (time.getHour() * 60 + time.getMinute());
    }
}
