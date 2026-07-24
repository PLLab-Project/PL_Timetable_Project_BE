package com.example.pl_timetable_project.timetable;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class InvalidTimetableException extends BusinessException {

    public InvalidTimetableException(String message) {
        super(TimetableErrorCode.INVALID_TIMETABLE, message);
    }
}
