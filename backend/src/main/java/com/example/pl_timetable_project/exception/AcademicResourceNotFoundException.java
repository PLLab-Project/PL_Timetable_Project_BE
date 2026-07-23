package com.example.pl_timetable_project.exception;

public class AcademicResourceNotFoundException extends ApplicationException {

    public AcademicResourceNotFoundException(String message) {
        super(ErrorCode.ACADEMIC_RESOURCE_NOT_FOUND, message);
    }
}
