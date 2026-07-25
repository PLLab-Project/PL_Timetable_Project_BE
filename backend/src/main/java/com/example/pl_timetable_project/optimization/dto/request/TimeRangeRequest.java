package com.example.pl_timetable_project.optimization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "하루 안의 시작·종료 시간 범위")
@Getter
@NoArgsConstructor
public class TimeRangeRequest {

    @Schema(description = "포함되는 시작 시각", example = "09:00:00", type = "string", format = "time")
    @NotNull
    private LocalTime startTime;

    @Schema(description = "포함되지 않는 종료 시각", example = "18:00:00", type = "string", format = "time")
    @NotNull
    private LocalTime endTime;

    public TimeRangeRequest(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
