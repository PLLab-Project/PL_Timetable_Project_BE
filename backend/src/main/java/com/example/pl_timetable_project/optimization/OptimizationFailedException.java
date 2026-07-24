package com.example.pl_timetable_project.optimization;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class OptimizationFailedException extends BusinessException {

    public OptimizationFailedException(String message) {
        super(OptimizationErrorCode.OPTIMIZATION_FAILED, message);
    }

    public OptimizationFailedException(String message, Throwable cause) {
        super(OptimizationErrorCode.OPTIMIZATION_FAILED, message);
        initCause(cause);
    }
}
