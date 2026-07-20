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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OptimizationJob 의 영속화/상태 전이만 담당하는 트랜잭션 경계.
 * 오래 걸리는 알고리즘 실행은 이 클래스 밖(비동기 스레드, 트랜잭션 없음)에서 이루어지고,
 * 그 결과를 짧은 트랜잭션으로 반영하기 위해 OptimizationService 와 분리했다.
 */
@Service
@RequiredArgsConstructor
public class OptimizationJobLifecycleService {

    private final OptimizationJobRepository jobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OptimizationJob createPendingJobAndPublish(Long userId, OptimizationCreateRequest request,
                                                        List<CandidateCourse> candidates,
                                                        OptimizationConstraints constraints) {
        if (userId == null) {
            throw new UnauthorizedException("userId 는 필수입니다.");
        }

        OptimizationJob job = new OptimizationJob(
                userId,
                request.getTimetableId(),
                request.getMinCredit(),
                request.getMaxCredit(),
                request.getTargetCredit(),
                request.getExcludedDays(),
                request.getRequiredCourseIds(),
                request.getAvailableTime().getStartTime(),
                request.getAvailableTime().getEndTime(),
                request.getLunchTime().getStartTime(),
                request.getLunchTime().getEndTime(),
                request.getMaxDailyClassMinutes());

        OptimizationJob saved = jobRepository.save(job);
        eventPublisher.publishEvent(new OptimizationJobCreatedEvent(saved.getId(), candidates, constraints));
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
    public OptimizationJob getOwnedJob(Long userId, Long jobId) {
        OptimizationJob job = jobRepository.findByIdWithResults(jobId)
                .orElseThrow(() -> new OptimizationJobNotFoundException(jobId));
        validateOwnership(job, userId);
        return job;
    }

    @Transactional
    public void cancel(Long userId, Long jobId) {
        OptimizationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new OptimizationJobNotFoundException(jobId));
        validateOwnership(job, userId);
        if (job.isFinished()) {
            throw new OptimizationAlreadyFinishedException(jobId);
        }
        job.markCancelled();
    }

    private void validateOwnership(OptimizationJob job, Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("userId 는 필수입니다.");
        }
        if (!job.getUserId().equals(userId)) {
            throw new ForbiddenException("해당 작업에 접근할 권한이 없습니다. id=" + job.getId());
        }
    }
}
