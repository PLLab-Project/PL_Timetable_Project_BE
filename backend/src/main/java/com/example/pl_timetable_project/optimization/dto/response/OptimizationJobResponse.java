package com.example.pl_timetable_project.optimization.dto.response;

import com.example.pl_timetable_project.optimization.entity.OptimizationJob;
import com.example.pl_timetable_project.optimization.entity.OptimizationJobStatus;
import com.example.pl_timetable_project.optimization.entity.OptimizationResult;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record OptimizationJobResponse(
        Long id,
        UUID userId,
        Long timetableId,
        String semesterId,
        OptimizationJobStatus status,
        String failureReason,
        List<OptimizationResultResponse> results,
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
