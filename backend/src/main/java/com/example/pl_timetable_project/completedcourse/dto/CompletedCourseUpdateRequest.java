package com.example.pl_timetable_project.completedcourse.dto;

import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "내 이수과목 부분 수정 요청. 생략하거나 null인 필드는 유지됩니다.")
public record CompletedCourseUpdateRequest(
        @Schema(description = "변경할 학교 과목코드", example = "855121")
        @Size(max = 40)
        String courseCode,

        @Schema(description = "변경할 과목명", example = "1인미디어제작실습")
        @Size(max = 240)
        String courseName,

        @Schema(description = "변경할 학점. 음수 불가, 소수 둘째 자리까지", example = "2.0")
        @DecimalMin("0.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal credits,

        @Schema(description = "변경할 이수구분", example = "전공선택")
        @Size(max = 160)
        String category,

        @Schema(description = "변경할 세부 영역", example = "전공심화")
        @Size(max = 120)
        String area,

        @Schema(description = "변경할 수강 학기", example = "2026-1")
        @Size(max = 20)
        String semester,

        @Schema(
                description = "변경할 수강 상태",
                example = "COMPLETED",
                allowableValues = {"COMPLETED", "IN_PROGRESS", "PLANNED", "FAILED", "WITHDRAWN"})
        CompletedCourseStatus status) {
}
