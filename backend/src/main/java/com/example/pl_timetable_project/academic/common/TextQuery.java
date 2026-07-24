package com.example.pl_timetable_project.academic.common;

import com.example.pl_timetable_project.exception.InvalidAcademicQueryException;

public final class TextQuery {

    private TextQuery() {
    }

    public static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String required(String value, String fieldName) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new InvalidAcademicQueryException(fieldName + "은(는) 필수입니다.");
        }
        return normalized;
    }
}
