package com.example.pl_timetable_project.exception;

public class RequiredCourseConflictException extends ApplicationException {

    public RequiredCourseConflictException(String message) {
        super(ErrorCode.REQUIRED_COURSE_CONFLICT, message);
    }
}
