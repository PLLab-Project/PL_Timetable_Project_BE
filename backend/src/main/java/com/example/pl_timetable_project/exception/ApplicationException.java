package com.example.pl_timetable_project.exception;

import lombok.Getter;

@Getter
public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApplicationException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    protected ApplicationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
