package com.example.pl_timetable_project.timetable.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TimetableCreateRequest {

    @NotBlank
    private String name;

    @NotNull
    private Integer year;

    @NotBlank
    private String semester;

    @Valid
    private List<TimetableCourseRequest> sections = new ArrayList<>();

    public TimetableCreateRequest(String name, Integer year, String semester, List<TimetableCourseRequest> sections) {
        this.name = name;
        this.year = year;
        this.semester = semester;
        this.sections = sections == null ? new ArrayList<>() : sections;
    }
}
