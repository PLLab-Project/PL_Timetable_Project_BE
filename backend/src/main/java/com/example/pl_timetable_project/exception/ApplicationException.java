package com.example.pl_timetable_project.exception;

import com.example.pl_timetable_project.common.exception.BusinessException;
import lombok.Getter;

@Getter
public abstract class ApplicationException extends BusinessException {

    private final ErrorCode errorCode;

    protected ApplicationException(ErrorCode errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    protected ApplicationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
        this.errorCode = errorCode;
    }
}
