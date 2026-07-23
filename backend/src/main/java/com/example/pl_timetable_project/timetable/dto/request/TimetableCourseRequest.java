package com.example.pl_timetable_project.timetable.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 분반의 정식 학사 키만 받는다. 과목명·교수·학점·시간은 서버가 DB에서 조회한다.
 */
@Getter
@NoArgsConstructor
public class TimetableCourseRequest {

    @NotBlank
    private String courseCode;

    @NotBlank
    private String sectionCode;

    public TimetableCourseRequest(String courseCode, String sectionCode) {
        this.courseCode = courseCode;
        this.sectionCode = sectionCode;
    }
}
