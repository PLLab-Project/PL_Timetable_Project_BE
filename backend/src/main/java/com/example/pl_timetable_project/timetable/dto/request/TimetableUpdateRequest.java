package com.example.pl_timetable_project.timetable.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TimetableUpdateRequest {

    @Size(min = 1, max = 120)
    private String name;

    public TimetableUpdateRequest(String name) {
        this.name = name;
    }
}
