package com.example.pl_timetable_project.exception;

public class InvalidTimetableException extends ApplicationException {

    public InvalidTimetableException(String message) {
        super(ErrorCode.INVALID_TIMETABLE, message);
    }
}
