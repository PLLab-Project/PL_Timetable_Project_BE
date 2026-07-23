package com.example.pl_timetable_project.timetable.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TimetableCreateRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    @Size(max = 20)
    private String semesterId;

    @Valid
    private List<TimetableCourseRequest> sections = new ArrayList<>();

    public TimetableCreateRequest(String name, String semesterId, List<TimetableCourseRequest> sections) {
        this.name = name;
        this.semesterId = semesterId;
        this.sections = sections == null ? new ArrayList<>() : sections;
    }
}
