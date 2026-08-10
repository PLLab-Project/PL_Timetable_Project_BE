package com.example.pl_timetable_project.completedcourse.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Schema(description = "시간표 이미지에서 인식한 과목별 수업 시간과 강의실")
public record RecognizedCourseMeetingResponse(
        @Schema(description = "수업 요일", example = "MONDAY")
        DayOfWeek dayOfWeek,

        @Schema(description = "시작 시각", example = "10:00")
        LocalTime startTime,

        @Schema(description = "종료 시각", example = "11:30")
        LocalTime endTime,

        @Schema(description = "이미지에 표시된 강의실", example = "공다A 411")
        String room,

        @Schema(
                description = "강의실 텍스트에 온라인·원격·사이버·비대면·e-learning 같은 "
                        + "표현이 있으면 true. 판단할 수 없으면 기본값 false",
                example = "false")
        boolean online) {
}
