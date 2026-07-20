package com.example.pl_timetable_project.exception;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class ErrorResponse {

    private final String code;
    private final String message;
    private final LocalDateTime timestamp;

    private ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message);
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message);
    }
}
