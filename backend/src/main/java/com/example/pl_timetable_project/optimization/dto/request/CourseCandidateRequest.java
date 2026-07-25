package com.example.pl_timetable_project.optimization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후보 분반 키만 받으며 과목명·학점·교수·수업시간은 서버가 학사 DB에서 조회한다.
 */
@Schema(description = "자동편성에서 검토할 학사 분반 후보")
@Getter
@NoArgsConstructor
public class CourseCandidateRequest {

    @Schema(description = "후보 과목의 학사 과목코드", example = "855121")
    @NotBlank
    private String courseCode;

    @Schema(description = "후보 과목의 분반 코드", example = "01")
    @NotBlank
    private String sectionCode;

    @Schema(
            description = "true이면 반드시 결과에 포함합니다. 필수 후보끼리 충돌하면 409",
            example = "true")
    private boolean required;

    public CourseCandidateRequest(String courseCode, String sectionCode, boolean required) {
        this.courseCode = courseCode;
        this.sectionCode = sectionCode;
        this.required = required;
    }
}
