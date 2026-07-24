package com.example.pl_timetable_project.timetable;

import com.example.pl_timetable_project.common.exception.ErrorCode;

/** 시간표 API에서 프론트에 전달하는 오류 코드입니다. */
public enum TimetableErrorCode implements ErrorCode {

    UNAUTHORIZED(401, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(403, "FORBIDDEN", "해당 시간표에 대한 권한이 없습니다."),
    INVALID_TIMETABLE(400, "INVALID_TIMETABLE", "요청한 시간표 정보가 올바르지 않습니다."),
    TIMETABLE_NOT_FOUND(404, "TIMETABLE_NOT_FOUND", "시간표를 찾을 수 없습니다."),
    SECTION_CONFLICT(409, "SECTION_CONFLICT", "강의 시간이 겹칩니다.");

    private final int status;
    private final String code;
    private final String message;

    TimetableErrorCode(int status, String code, String message) {
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
