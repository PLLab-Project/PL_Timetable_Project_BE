package com.example.pl_timetable_project.exception;

public class OptimizationFailedException extends ApplicationException {

    public OptimizationFailedException(String message) {
        super(ErrorCode.OPTIMIZATION_FAILED, message);
    }

    public OptimizationFailedException(String message, Throwable cause) {
        super(ErrorCode.OPTIMIZATION_FAILED, message);
        initCause(cause);
    }
}
