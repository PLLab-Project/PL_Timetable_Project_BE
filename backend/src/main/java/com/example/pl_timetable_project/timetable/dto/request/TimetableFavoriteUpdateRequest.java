package com.example.pl_timetable_project.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "시간표 즐겨찾기 상태 변경 요청")
public record TimetableFavoriteUpdateRequest(
        @Schema(description = "즐겨찾기 여부. 다른 시간표의 상태에는 영향 없음", example = "true")
        @NotNull
        Boolean favorite) {
}
