package com.example.pl_timetable_project.timetable;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(TimetableErrorCode.UNAUTHORIZED, message);
    }
}
