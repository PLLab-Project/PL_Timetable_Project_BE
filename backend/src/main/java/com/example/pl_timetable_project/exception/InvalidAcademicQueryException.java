package com.example.pl_timetable_project.exception;

public class InvalidAcademicQueryException extends ApplicationException {

    public InvalidAcademicQueryException(String message) {
        super(ErrorCode.INVALID_ACADEMIC_QUERY, message);
    }
}
