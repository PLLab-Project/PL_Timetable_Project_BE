package com.example.pl_timetable_project.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "시간표 이름 부분 수정 요청")
@Getter
@NoArgsConstructor
public class TimetableUpdateRequest {

    @Schema(description = "새 시간표 이름. 1~120자", example = "공강 우선 시간표")
    @Size(min = 1, max = 120)
    private String name;

    public TimetableUpdateRequest(String name) {
        this.name = name;
    }
}
