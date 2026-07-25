package com.example.pl_timetable_project.academic.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "학사 목록 API의 0부터 시작하는 페이지 응답")
public record AcademicPageResponse<T>(
        @Schema(description = "현재 페이지의 항목 목록")
        List<T> items,

        @Schema(description = "0부터 시작하는 현재 페이지 번호", example = "0")
        int page,

        @Schema(description = "요청에 적용된 페이지 크기", example = "20")
        int size,

        @Schema(description = "조회 조건에 해당하는 전체 항목 수", example = "1576")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "79")
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
