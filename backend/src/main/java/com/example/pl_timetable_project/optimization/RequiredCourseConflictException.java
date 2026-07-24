package com.example.pl_timetable_project.optimization;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class RequiredCourseConflictException extends BusinessException {

    public RequiredCourseConflictException(String message) {
        super(OptimizationErrorCode.REQUIRED_COURSE_CONFLICT, message);
    }
}
