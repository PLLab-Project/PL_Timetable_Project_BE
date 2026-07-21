package com.example.pl_timetable_project.user;

import com.example.pl_timetable_project.common.exception.ErrorCode;

/** 회원 API가 프론트에 전달하는 오류 코드입니다. */
public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다."),
    DEPARTMENT_NOT_FOUND(404, "DEPARTMENT_NOT_FOUND", "학과 정보를 찾을 수 없습니다."),
    CONFIRMATION_REQUIRED(400, "CONFIRMATION_REQUIRED", "회원 탈퇴 확인이 필요합니다.");

    private final int status;
    private final String code;
    private final String message;

    UserErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public int status() { return status; }
    public String code() { return code; }
    public String message() { return message; }
}
