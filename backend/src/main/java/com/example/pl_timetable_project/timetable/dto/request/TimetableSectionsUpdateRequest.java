package com.example.pl_timetable_project.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시간표의 강의 구성을 통째로 교체하는 요청.
 */
@Schema(description = "시간표의 전체 분반 구성을 한 번에 교체하는 요청")
@Getter
@NoArgsConstructor
public class TimetableSectionsUpdateRequest {

    @Schema(
            description = "교체 후 시간표에 남길 전체 분반 목록. 빈 배열이면 모든 분반 제거",
            example = "[{\"courseCode\":\"855121\",\"sectionCode\":\"01\"}]")
    @NotNull
    @Valid
    private List<TimetableCourseRequest> sections;

    public TimetableSectionsUpdateRequest(List<TimetableCourseRequest> sections) {
        this.sections = sections;
    }
}
