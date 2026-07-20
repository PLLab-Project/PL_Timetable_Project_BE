package com.example.pl_timetable_project.timetable.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시간표에 담길 강의 한 요일-시간 슬롯에 대한 요청 정보.
 * 같은 courseId 를 가진 항목을 여러 개 넣으면 주 여러 요일 강의를 표현할 수 있다.
 */
@Getter
@NoArgsConstructor
public class TimetableCourseRequest {

    @NotNull
    private Long courseId;

    @NotBlank
    private String courseName;

    private String professorName;

    @NotNull
    @Min(1)
    private Integer credit;

    @NotNull
    private DayOfWeek dayOfWeek;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    public TimetableCourseRequest(Long courseId, String courseName, String professorName, Integer credit,
                                   DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.professorName = professorName;
        this.credit = credit;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
