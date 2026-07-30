package com.example.pl_timetable_project.optimization.entity;

import com.example.pl_timetable_project.optimization.algorithm.OptimizationTimeRange;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptimizationAvailableTime {

    @Column(name = "start_minute", nullable = false)
    private Short startMinute;

    @Column(name = "end_minute", nullable = false)
    private Short endMinute;

    public OptimizationAvailableTime(OptimizationTimeRange range) {
        startMinute = toMinute(range.startTime());
        endMinute = toMinute(range.endTime());
    }

    private static short toMinute(LocalTime time) {
        return (short) (time.getHour() * 60 + time.getMinute());
    }
}
