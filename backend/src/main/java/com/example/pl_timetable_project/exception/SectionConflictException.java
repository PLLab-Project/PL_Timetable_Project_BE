package com.example.pl_timetable_project.exception;

public class SectionConflictException extends ApplicationException {

    public SectionConflictException(String message) {
        super(ErrorCode.SECTION_CONFLICT, message);
    }
}
