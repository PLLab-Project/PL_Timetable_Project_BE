package com.example.pl_timetable_project.timetable.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시간표 이름/연도/학기 부분 수정 요청. null 인 필드는 변경하지 않는다.
 */
@Getter
@NoArgsConstructor
public class TimetableUpdateRequest {

    private String name;

    private Integer year;

    private String semester;

    public TimetableUpdateRequest(String name, Integer year, String semester) {
        this.name = name;
        this.year = year;
        this.semester = semester;
    }
}
