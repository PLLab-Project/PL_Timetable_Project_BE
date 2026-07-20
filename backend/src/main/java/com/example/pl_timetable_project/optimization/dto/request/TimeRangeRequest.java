package com.example.pl_timetable_project.optimization.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TimeRangeRequest {

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    public TimeRangeRequest(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
