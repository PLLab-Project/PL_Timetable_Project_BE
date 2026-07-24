package com.example.pl_timetable_project.optimization.dto.response;

import com.example.pl_timetable_project.optimization.entity.OptimizationJob;
import com.example.pl_timetable_project.optimization.entity.OptimizationJobStatus;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;

@Getter
public class OptimizationJobResponse {

    private final Long id;
    private final Long userId;
    private final Long timetableId;
    private final OptimizationJobStatus status;
    private final String failureReason;
    private final List<OptimizationResultResponse> results;
    private final LocalDateTime createdAt;

    private OptimizationJobResponse(Long id, Long userId, Long timetableId, OptimizationJobStatus status,
                                     String failureReason, List<OptimizationResultResponse> results,
                                     LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.timetableId = timetableId;
        this.status = status;
        this.failureReason = failureReason;
        this.results = results;
        this.createdAt = createdAt;
    }

    public static OptimizationJobResponse from(OptimizationJob job) {
        List<OptimizationResultResponse> results = job.getResults().stream()
                .sorted(Comparator.comparingInt(r -> r.getRank()))
                .map(OptimizationResultResponse::from)
                .toList();
        return new OptimizationJobResponse(
                job.getId(),
                job.getUserId(),
                job.getTimetableId(),
                job.getStatus(),
                job.getFailureReason(),
                results,
                job.getCreatedAt());
    }
}
