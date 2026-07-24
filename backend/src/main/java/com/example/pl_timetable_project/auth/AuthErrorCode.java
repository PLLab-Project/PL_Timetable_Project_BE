package com.example.pl_timetable_project.auth;

import com.example.pl_timetable_project.common.exception.ErrorCode;

/** 인증 API에서 프론트에 전달하는 오류 코드입니다. */
public enum AuthErrorCode implements ErrorCode {

    INVALID_STUDENT_NUMBER(400, "INVALID_STUDENT_NUMBER", "학번 형식이 올바르지 않습니다."),
    INVALID_CODE_FORMAT(400, "INVALID_CODE_FORMAT", "인증번호는 숫자 6자리여야 합니다."),
    INVALID_OR_EXPIRED_CODE(401, "INVALID_OR_EXPIRED_CODE", "인증번호가 틀렸거나 만료되었습니다."),
    SESSION_EXPIRED(401, "SESSION_EXPIRED", "로그인 세션이 만료되었습니다."),
    ACCOUNT_DISABLED(403, "ACCOUNT_DISABLED", "탈퇴하거나 정지된 계정입니다."),
    TOO_MANY_REQUESTS(429, "TOO_MANY_REQUESTS", "잠시 후 인증번호를 다시 요청해주세요."),
    TOO_MANY_ATTEMPTS(429, "TOO_MANY_ATTEMPTS", "인증번호 확인 횟수를 초과했습니다."),
    EMAIL_SEND_FAILED(503, "EMAIL_SEND_FAILED", "인증 이메일을 전송하지 못했습니다.");

    private final int status;
    private final String code;
    private final String message;

    AuthErrorCode(int status, String code, String message) {
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
