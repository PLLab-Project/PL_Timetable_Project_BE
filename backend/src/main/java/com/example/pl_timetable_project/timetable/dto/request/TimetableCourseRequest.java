package com.example.pl_timetable_project.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 분반의 정식 학사 키만 받는다. 과목명·교수·학점·시간은 서버가 DB에서 조회한다.
 */
@Schema(description = "시간표에 넣을 정식 학사 분반 키")
@Getter
@NoArgsConstructor
public class TimetableCourseRequest {

    @Schema(description = "학사 DB의 과목코드", example = "855121")
    @NotBlank
    private String courseCode;

    @Schema(description = "해당 학기·과목 안에서 분반을 식별하는 코드", example = "01")
    @NotBlank
    private String sectionCode;

    public TimetableCourseRequest(String courseCode, String sectionCode) {
        this.courseCode = courseCode;
        this.sectionCode = sectionCode;
    }
}
