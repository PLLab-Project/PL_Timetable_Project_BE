package com.example.pl_timetable_project.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode implements com.example.pl_timetable_project.common.exception.ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_ACADEMIC_QUERY(HttpStatus.BAD_REQUEST, "학사 데이터 조회 조건이 올바르지 않습니다."),
    ACADEMIC_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "학사 데이터를 찾을 수 없습니다."),
    INVALID_TIMETABLE(HttpStatus.BAD_REQUEST, "요청한 시간표 정보가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "해당 시간표에 대한 권한이 없습니다."),
    TIMETABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "시간표를 찾을 수 없습니다."),
    SECTION_CONFLICT(HttpStatus.CONFLICT, "강의 시간이 겹칩니다."),

    INVALID_OPTIMIZATION_CONDITION(HttpStatus.BAD_REQUEST, "자동 편성 조건이 올바르지 않습니다."),
    OPTIMIZATION_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "편성 작업을 찾을 수 없습니다."),
    REQUIRED_COURSE_CONFLICT(HttpStatus.CONFLICT, "필수 강의끼리 시간이 겹칩니다."),
    REQUIRED_COURSE_EXCLUDED_BY_CONDITION(HttpStatus.CONFLICT, "필수 강의 중 후보 목록에 없거나 조건(제외 요일/수업 가능 시간대)에 맞지 않는 강의가 있습니다."),
    OPTIMIZATION_ALREADY_FINISHED(HttpStatus.CONFLICT, "이미 종료된 작업은 취소할 수 없습니다."),
    NO_FEASIBLE_TIMETABLE(HttpStatus.valueOf(422), "조건에 맞는 시간표를 찾을 수 없습니다."),
    OPTIMIZATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "시간표 편성 시간이 초과되었습니다."),
    OPTIMIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "시간표 편성 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    @Override
    public int status() {
        return status.value();
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String message() {
        return defaultMessage;
    }
}
