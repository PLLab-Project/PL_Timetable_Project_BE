package com.example.pl_timetable_project.optimization.service;

import com.example.pl_timetable_project.exception.ForbiddenException;
import com.example.pl_timetable_project.exception.OptimizationAlreadyFinishedException;
import com.example.pl_timetable_project.exception.OptimizationJobNotFoundException;
import com.example.pl_timetable_project.exception.UnauthorizedException;
import com.example.pl_timetable_project.optimization.algorithm.CandidateCourse;
import com.example.pl_timetable_project.optimization.algorithm.OptimizationConstraints;
import com.example.pl_timetable_project.optimization.dto.request.OptimizationCreateRequest;
import com.example.pl_timetable_project.optimization.entity.OptimizationJob;
import com.example.pl_timetable_project.optimization.entity.OptimizationJobStatus;
import com.example.pl_timetable_project.optimization.entity.OptimizationResult;
import com.example.pl_timetable_project.optimization.repository.OptimizationJobRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OptimizationJobLifecycleService {

    private final OptimizationJobRepository jobRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OptimizationJobLifecycleService(
            OptimizationJobRepository jobRepository,
            ApplicationEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OptimizationJob createPendingJobAndPublish(
            UUID userId,
            String semesterId,
            OptimizationCreateRequest request,
            List<CandidateCourse> candidates,
            OptimizationConstraints constraints) {
        validateUserId(userId);
        OptimizationJob job = new OptimizationJob(
                userId,
                request.getTimetableId(),
                semesterId,
                request.getMinCredits(),
                request.getMaxCredits(),
                request.getTargetCredits(),
                constraints.excludedDays(),
                constraints.requiredSections(),
                request.getAvailableTime().getStartTime(),
                request.getAvailableTime().getEndTime(),
                request.getLunchTime().getStartTime(),
                request.getLunchTime().getEndTime(),
                request.getMaxDailyClassMinutes());
        OptimizationJob saved = jobRepository.save(job);
        eventPublisher.publishEvent(
                new OptimizationJobCreatedEvent(saved.getId(), candidates, constraints));
        return saved;
    }

    @Transactional
    public boolean tryMarkProcessing(Long jobId) {
        return jobRepository.findById(jobId)
                .filter(job -> job.getStatus() == OptimizationJobStatus.PENDING)
                .map(job -> {
                    job.markProcessing();
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void finalizeSuccess(Long jobId, List<OptimizationResult> results) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() != OptimizationJobStatus.CANCELLED) {
                job.markSuccess(results);
            }
        });
    }

    @Transactional
    public void finalizeFailed(Long jobId, String reason) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() != OptimizationJobStatus.CANCELLED) {
                job.markFailed(reason);
            }
        });
    }

    @Transactional
    public void finalizeTimeout(Long jobId, String reason) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() != OptimizationJobStatus.CANCELLED) {
                job.markTimeout(reason);
            }
        });
    }

    @Transactional(readOnly = true)
    public OptimizationJob getOwnedJob(UUID userId, Long jobId) {
        OptimizationJob job = jobRepository.findByIdWithResults(jobId)
                .orElseThrow(() -> new OptimizationJobNotFoundException(jobId));
        validateOwnership(job, userId);
        return job;
    }

    @Transactional
    public void cancel(UUID userId, Long jobId) {
        OptimizationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new OptimizationJobNotFoundException(jobId));
        validateOwnership(job, userId);
        if (job.isFinished()) {
            throw new OptimizationAlreadyFinishedException(jobId);
        }
        job.markCancelled();
    }

    private void validateOwnership(OptimizationJob job, UUID userId) {
        validateUserId(userId);
        if (!job.getUserId().equals(userId)) {
            throw new ForbiddenException("해당 작업에 접근할 권한이 없습니다. id=" + job.getId());
        }
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("userId 는 필수입니다.");
        }
    }
}
