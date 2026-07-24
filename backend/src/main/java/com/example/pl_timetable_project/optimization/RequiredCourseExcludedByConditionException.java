package com.example.pl_timetable_project.optimization;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class RequiredCourseExcludedByConditionException extends BusinessException {

    public RequiredCourseExcludedByConditionException(String message) {
        super(OptimizationErrorCode.REQUIRED_COURSE_EXCLUDED_BY_CONDITION, message);
    }
}
