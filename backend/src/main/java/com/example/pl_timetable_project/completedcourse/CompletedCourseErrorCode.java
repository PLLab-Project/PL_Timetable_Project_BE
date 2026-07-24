package com.example.pl_timetable_project.completedcourse;

import com.example.pl_timetable_project.common.exception.ErrorCode;

/** 이수과목 API의 안정적인 오류 코드입니다. */
public enum CompletedCourseErrorCode implements ErrorCode {
    INVALID_REQUEST(400, "COMPLETED_COURSE_INVALID_REQUEST", "이수과목 요청값이 올바르지 않습니다."),
    NOT_FOUND(404, "COMPLETED_COURSE_NOT_FOUND", "이수과목을 찾을 수 없습니다."),
    TIMETABLE_NOT_FOUND(404, "COMPLETED_COURSE_TIMETABLE_NOT_FOUND", "가져올 시간표를 찾을 수 없습니다."),
    INVALID_STATUS_TRANSITION(
            409,
            "COMPLETED_COURSE_INVALID_STATUS_TRANSITION",
            "수강 중인 과목만 이수 완료로 전환할 수 있습니다.");

    private final int status;
    private final String code;
    private final String message;

    CompletedCourseErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
