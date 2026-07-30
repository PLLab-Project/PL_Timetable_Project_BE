package com.example.pl_timetable_project.timetable.dto.response;

import com.example.pl_timetable_project.timetable.entity.Timetable;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "검증된 분반 구성과 계산 결과를 포함하는 내 시간표 상세")
public record TimetableResponse(
        @Schema(description = "시간표 ID", example = "12")
        Long id,

        @Schema(description = "시간표 소유 사용자 UUID")
        UUID userId,

        @Schema(description = "사용자가 지정한 시간표 이름", example = "2026-1 전공 시간표")
        String name,

        @Schema(description = "시간표가 속한 학기 ID", example = "2026-1")
        String semesterId,

        @Schema(description = "즐겨찾기 여부")
        boolean favorite,

        @Schema(description = "포함된 분반의 총학점", example = "15.0")
        BigDecimal totalCredits,

        @Schema(description = "학사 DB에서 검증해 저장한 분반 목록")
        List<TimetableCourseResponse> sections,

        @Schema(description = "같은 요일 수업 사이에 생기는 공강 구간 목록")
        List<FreeTimeResponse> freeTimes,

        @Schema(description = "시간표 생성 시각")
        Instant createdAt,

        @Schema(description = "시간표 이름 또는 구성의 마지막 수정 시각")
        Instant updatedAt) {

    public static TimetableResponse of(
            Timetable timetable,
            BigDecimal totalCredits,
            List<TimetableCourseResponse> sections,
            List<FreeTimeResponse> freeTimes) {
        return new TimetableResponse(
                timetable.getId(),
                timetable.getUserId(),
                timetable.getName(),
                timetable.getSemesterId(),
                timetable.isFavorite(),
                totalCredits,
                sections,
                freeTimes,
                timetable.getCreatedAt(),
                timetable.getUpdatedAt());
    }
}
