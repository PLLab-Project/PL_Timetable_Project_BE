package com.example.pl_timetable_project.exception;

public class InvalidOptimizationConditionException extends ApplicationException {

    public InvalidOptimizationConditionException(String message) {
        super(ErrorCode.INVALID_OPTIMIZATION_CONDITION, message);
    }
}
