package com.example.pl_timetable_project.optimization;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class InvalidOptimizationConditionException extends BusinessException {

    public InvalidOptimizationConditionException(String message) {
        super(OptimizationErrorCode.INVALID_OPTIMIZATION_CONDITION, message);
    }
}
