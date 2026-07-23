package com.example.pl_timetable_project.optimization.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후보 분반 키만 받으며 과목명·학점·교수·수업시간은 서버가 학사 DB에서 조회한다.
 */
@Getter
@NoArgsConstructor
public class CourseCandidateRequest {

    @NotBlank
    private String courseCode;

    @NotBlank
    private String sectionCode;

    private boolean required;

    public CourseCandidateRequest(String courseCode, String sectionCode, boolean required) {
        this.courseCode = courseCode;
        this.sectionCode = sectionCode;
        this.required = required;
    }
}
