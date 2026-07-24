package com.example.pl_timetable_project.exception;

public class RequiredCourseExcludedByConditionException extends ApplicationException {

    public RequiredCourseExcludedByConditionException(String message) {
        super(ErrorCode.REQUIRED_COURSE_EXCLUDED_BY_CONDITION, message);
    }
}
