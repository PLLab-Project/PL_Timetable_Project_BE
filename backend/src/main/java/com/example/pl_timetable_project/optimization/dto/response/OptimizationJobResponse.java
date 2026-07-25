package com.example.pl_timetable_project.optimization.dto.response;

import com.example.pl_timetable_project.optimization.entity.OptimizationJob;
import com.example.pl_timetable_project.optimization.entity.OptimizationJobStatus;
import com.example.pl_timetable_project.optimization.entity.OptimizationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Schema(description = "비동기 시간표 자동편성 작업의 현재 상태와 결과")
public record OptimizationJobResponse(
        @Schema(description = "자동편성 작업 ID", example = "42")
        Long id,

        @Schema(description = "작업을 생성한 사용자 UUID")
        UUID userId,

        @Schema(description = "작업 결과를 연결할 시간표 ID", example = "12")
        Long timetableId,

        @Schema(description = "후보 분반이 속한 학기 ID", example = "2026-1")
        String semesterId,

        @Schema(
                description = "작업 상태. RUNNING은 재조회하고 종료 상태에서는 결과 또는 실패 이유 확인",
                example = "SUCCEEDED")
        OptimizationJobStatus status,

        @Schema(description = "실패 또는 시간 초과 시 원인. 성공하거나 실행 중이면 null")
        String failureReason,

        @Schema(description = "점수순 최대 3개의 후보 시간표. 완료 전에는 빈 배열")
        List<OptimizationResultResponse> results,

        @Schema(description = "작업 생성 시각")
        Instant createdAt) {

    public static OptimizationJobResponse from(OptimizationJob job) {
        return new OptimizationJobResponse(
                job.getId(),
                job.getUserId(),
                job.getTimetableId(),
                job.getSemesterId(),
                job.getStatus(),
                job.getFailureReason(),
                job.getResults().stream()
                        .sorted(Comparator.comparingInt(OptimizationResult::getRank))
                        .map(OptimizationResultResponse::from)
                        .toList(),
                job.getCreatedAt());
    }
}
