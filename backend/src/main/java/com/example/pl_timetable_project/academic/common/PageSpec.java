package com.example.pl_timetable_project.academic.common;

import com.example.pl_timetable_project.exception.InvalidAcademicQueryException;

public record PageSpec(int page, int size) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static PageSpec of(int page, int size) {
        if (page < 0) {
            throw new InvalidAcademicQueryException("page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidAcademicQueryException(
                    "size는 1 이상 " + MAX_SIZE + " 이하여야 합니다.");
        }
        return new PageSpec(page, size);
    }

    public long offset() {
        return (long) page * size;
    }
}
