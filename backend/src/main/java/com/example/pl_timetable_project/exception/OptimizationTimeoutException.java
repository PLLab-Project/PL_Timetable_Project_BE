package com.example.pl_timetable_project.exception;

public class OptimizationTimeoutException extends ApplicationException {

    public OptimizationTimeoutException(String message) {
        super(ErrorCode.OPTIMIZATION_TIMEOUT, message);
    }
}
