package com.example.pl_timetable_project.timetable;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class SectionConflictException extends BusinessException {

    public SectionConflictException(String message) {
        super(TimetableErrorCode.SECTION_CONFLICT, message);
    }
}
