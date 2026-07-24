package com.example.pl_timetable_project.academic.common;

import java.util.List;

public record AcademicPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> AcademicPageResponse<T> of(
            List<T> items, PageSpec pageSpec, long totalElements) {
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / pageSpec.size());
        return new AcademicPageResponse<>(
                List.copyOf(items),
                pageSpec.page(),
                pageSpec.size(),
                totalElements,
                totalPages);
    }
}
