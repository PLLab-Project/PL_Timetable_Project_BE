package com.example.pl_timetable_project.exception;

public class ForbiddenException extends ApplicationException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
