package com.example.pl_timetable_project.exception;

public class UnauthorizedException extends ApplicationException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
