package com.example.pl_timetable_project.exception;

public class AlreadyCompletedCourseException extends ApplicationException {

    public AlreadyCompletedCourseException(String message) {
        super(ErrorCode.ALREADY_COMPLETED_COURSE, message);
    }
}
