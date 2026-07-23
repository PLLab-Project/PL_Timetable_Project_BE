package com.example.pl_timetable_project.timetable.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시간표의 강의 구성을 통째로 교체하는 요청.
 */
@Getter
@NoArgsConstructor
public class TimetableSectionsUpdateRequest {

    @NotNull
    @Valid
    private List<TimetableCourseRequest> sections;

    public TimetableSectionsUpdateRequest(List<TimetableCourseRequest> sections) {
        this.sections = sections;
    }
}
