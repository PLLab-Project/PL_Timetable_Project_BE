package com.example.pl_timetable_project.optimization.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 편성 후보로 검토할 강의 한 건. Course 도메인이 별도로 없어
 * 요청 시점에 후보 강의 목록(courseId, 시간대 등)을 그대로 함께 넘겨받는다.
 */
@Getter
@NoArgsConstructor
public class CourseCandidateRequest {

    @NotNull
    private Long courseId;

    @NotBlank
    private String courseName;

    private String professorName;

    @NotNull
    @Min(1)
    private Integer credit;

    @NotEmpty
    @Valid
    private List<TimeSlotRequest> timeSlots;

    public CourseCandidateRequest(Long courseId, String courseName, String professorName, Integer credit,
                                   List<TimeSlotRequest> timeSlots) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.professorName = professorName;
        this.credit = credit;
        this.timeSlots = timeSlots;
    }
}
