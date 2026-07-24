package com.example.pl_timetable_project.common.exception;

import java.util.Objects;

/** 예상 가능한 비즈니스 오류를 전달하는 예외입니다. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").message());
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
