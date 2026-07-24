package com.example.pl_timetable_project.optimization;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class OptimizationTimeoutException extends BusinessException {

    public OptimizationTimeoutException(String message) {
        super(OptimizationErrorCode.OPTIMIZATION_TIMEOUT, message);
    }
}
