package com.example.pl_timetable_project.optimization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Schema(description = "자동편성 결과에서 반드시 비워 둘 요일별 시간 범위")
public record BlockedTimeRequest(
        @NotNull
        @Schema(description = "비워 둘 요일", example = "WEDNESDAY")
        DayOfWeek dayOfWeek,

        @NotNull
        @Schema(
                description = "공강 고정 시작 시각",
                example = "13:00:00",
                type = "string",
                format = "time")
        LocalTime startTime,

        @NotNull
        @Schema(
                description = "공강 고정 종료 시각",
                example = "15:00:00",
                type = "string",
                format = "time")
        LocalTime endTime) {
}
