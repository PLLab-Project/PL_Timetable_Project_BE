package com.example.pl_timetable_project.academic.course;

import com.example.pl_timetable_project.exception.InvalidAcademicQueryException;
import java.util.Locale;

public enum CourseSort {
    NAME_ASC,
    NAME_DESC,
    REVIEW_COUNT_DESC,
    RATING_DESC,
    POPULARITY_DESC;

    public static CourseSort parse(String value) {
        if (value == null || value.isBlank()) {
            return NAME_ASC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidAcademicQueryException(
                    "sort는 NAME_ASC, NAME_DESC, REVIEW_COUNT_DESC, "
                            + "RATING_DESC, POPULARITY_DESC 중 하나여야 합니다.");
        }
    }
}
