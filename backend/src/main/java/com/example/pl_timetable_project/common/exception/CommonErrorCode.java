package com.example.pl_timetable_project.common.exception;

/** 공통 계층에서 사용하는 오류 코드입니다. */
public enum CommonErrorCode implements ErrorCode {

    INVALID_REQUEST(400, "INVALID_REQUEST", "요청값이 올바르지 않습니다."),
    UNAUTHORIZED(401, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(403, "FORBIDDEN", "요청한 작업을 수행할 권한이 없습니다."),
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),
    SERVICE_UNAVAILABLE(503, "SERVICE_UNAVAILABLE", "현재 서비스를 사용할 수 없습니다.");

    private final int status;
    private final String code;
    private final String message;

    CommonErrorCode(int status, String code, String message) {
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
