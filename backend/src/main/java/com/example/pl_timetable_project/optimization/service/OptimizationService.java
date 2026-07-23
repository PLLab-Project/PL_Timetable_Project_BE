package com.example.pl_timetable_project.optimization.service;

import com.example.pl_timetable_project.exception.InvalidOptimizationConditionException;
import com.example.pl_timetable_project.exception.NoFeasibleTimetableException;
import com.example.pl_timetable_project.exception.OptimizationFailedException;
import com.example.pl_timetable_project.exception.OptimizationTimeoutException;
import com.example.pl_timetable_project.exception.RequiredCourseConflictException;
import com.example.pl_timetable_project.optimization.algorithm.CandidateCourse;
import com.example.pl_timetable_project.optimization.algorithm.CandidateCourseFilter;
import com.example.pl_timetable_project.optimization.algorithm.CourseTimeSlot;
import com.example.pl_timetable_project.optimization.algorithm.OptimizationConstraints;
import com.example.pl_timetable_project.optimization.algorithm.RequiredCoursePlacer;
import com.example.pl_timetable_project.optimization.algorithm.RequiredPlacementResult;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleCombination;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleScorer;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleSearchService;
import com.example.pl_timetable_project.optimization.algorithm.ScoredCombination;
import com.example.pl_timetable_project.optimization.dto.request.CourseCandidateRequest;
import com.example.pl_timetable_project.optimization.dto.request.OptimizationCreateRequest;
import com.example.pl_timetable_project.optimization.dto.response.OptimizationJobResponse;
import com.example.pl_timetable_project.optimization.entity.CourseSlot;
import com.example.pl_timetable_project.optimization.entity.OptimizationJob;
import com.example.pl_timetable_project.optimization.entity.OptimizationResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 자동 편성 작업의 진입점. 학점 범위 등 즉시 확인 가능한 조건은 Job 생성 시점에 동기로 검증하고,
 * 실제 조합 탐색은 Job 커밋 후 비동기로 실행해 Job 생명주기(PENDING→PROCESSING→SUCCESS/FAILED/TIMEOUT/CANCELLED)를 관리한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimizationService {

    private static final long SEARCH_TIME_LIMIT_MILLIS = 10_000;

    private final OptimizationJobLifecycleService lifecycleService;
    private final CandidateCourseFilter candidateCourseFilter;
    private final RequiredCoursePlacer requiredCoursePlacer;
    private final ScheduleSearchService scheduleSearchService;
    private final ScheduleScorer scheduleScorer;

    public OptimizationJobResponse createJob(Long userId, OptimizationCreateRequest request) {
        validateCreditRange(request);

        List<CandidateCourse> candidates = toCandidateCourses(request.getCandidateCourses());
        OptimizationConstraints constraints = buildConstraints(request);

        // 필수 강의 충돌 등 값싸게 확인 가능한 조건은 Job 을 만들기 전에 먼저 검증해 즉시 실패시킨다.
        List<CandidateCourse> filtered = candidateCourseFilter.filter(candidates, constraints);
        requiredCoursePlacer.place(filtered, constraints.requiredCourseIds());

        OptimizationJob job = lifecycleService.createPendingJobAndPublish(userId, request, candidates, constraints);
        return OptimizationJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public OptimizationJobResponse getJob(Long userId, Long jobId) {
        return OptimizationJobResponse.from(lifecycleService.getOwnedJob(userId, jobId));
    }

    public void cancelJob(Long userId, Long jobId) {
        lifecycleService.cancel(userId, jobId);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleJobCreated(OptimizationJobCreatedEvent event) {
        Long jobId = event.jobId();
        if (!lifecycleService.tryMarkProcessing(jobId)) {
            return;
        }

        try {
            List<CandidateCourse> filtered = candidateCourseFilter.filter(event.candidates(), event.constraints());
            RequiredPlacementResult placement = requiredCoursePlacer.place(filtered, event.constraints().requiredCourseIds());
            List<ScheduleCombination> combinations = scheduleSearchService.search(
                    placement.requiredCourses(), placement.optionalCandidates(), event.constraints());

            if (combinations.isEmpty()) {
                throw new NoFeasibleTimetableException("조건에 맞는 시간표 조합을 찾지 못했습니다. jobId=" + jobId);
            }

            // scheduleSearchService.search()가 CP-SAT 목적함수(ScheduleScorer와 동일한 점수식)로
            // 이미 서로 다른 상위 3개를 찾아 최적 순서대로 반환하므로, 여기서는 결과 저장에 필요한
            // 점수/등교일수/공강시간 등 부가 지표만 계산한다(순위 재산정이나 재선정은 하지 않는다).
            List<ScoredCombination> scored = combinations.stream()
                    .map(combination -> scheduleScorer.score(combination, event.constraints()))
                    .toList();

            lifecycleService.finalizeSuccess(jobId, toOptimizationResults(scored));
        } catch (OptimizationTimeoutException e) {
            lifecycleService.finalizeTimeout(jobId, e.getMessage());
        } catch (RequiredCourseConflictException | InvalidOptimizationConditionException | NoFeasibleTimetableException e) {
            lifecycleService.finalizeFailed(jobId, e.getMessage());
        } catch (Exception e) {
            log.error("시간표 자동 편성 중 예상치 못한 오류가 발생했습니다. jobId={}", jobId, e);
            OptimizationFailedException failure = new OptimizationFailedException("시간표 편성 중 오류가 발생했습니다.", e);
            lifecycleService.finalizeFailed(jobId, failure.getMessage());
        }
    }

    private void validateCreditRange(OptimizationCreateRequest request) {
        if (request.getMinCredit() > request.getMaxCredit()) {
            throw new InvalidOptimizationConditionException("최소학점은 최대학점보다 클 수 없습니다.");
        }
        if (request.getTargetCredit() < request.getMinCredit() || request.getTargetCredit() > request.getMaxCredit()) {
            throw new InvalidOptimizationConditionException("목표학점은 최소학점과 최대학점 사이여야 합니다.");
        }
    }

    private OptimizationConstraints buildConstraints(OptimizationCreateRequest request) {
        return new OptimizationConstraints(
                request.getMinCredit(),
                request.getMaxCredit(),
                request.getTargetCredit(),
                request.getExcludedDays(),
                request.getRequiredCourseIds(),
                request.getAvailableTime().getStartTime(),
                request.getAvailableTime().getEndTime(),
                request.getLunchTime().getStartTime(),
                request.getLunchTime().getEndTime(),
                request.getMaxDailyClassMinutes(),
                SEARCH_TIME_LIMIT_MILLIS);
    }

    private List<CandidateCourse> toCandidateCourses(List<CourseCandidateRequest> requests) {
        return requests.stream()
                .map(request -> new CandidateCourse(
                        request.getCourseId(),
                        request.getCourseName(),
                        request.getProfessorName(),
                        request.getCredit(),
                        request.getTimeSlots().stream()
                                .map(slot -> new CourseTimeSlot(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
                                .toList()))
                .toList();
    }

    private List<OptimizationResult> toOptimizationResults(List<ScoredCombination> topCombinations) {
        List<OptimizationResult> results = new ArrayList<>();
        int rank = 1;
        for (ScoredCombination scored : topCombinations) {
            List<CourseSlot> courseSlots = scored.combination().courses().stream()
                    .flatMap(course -> course.timeSlots().stream()
                            .map(slot -> new CourseSlot(course.courseId(), course.courseName(), course.professorName(),
                                    course.credit(), slot.dayOfWeek(), slot.startTime(), slot.endTime())))
                    .toList();
            results.add(new OptimizationResult(
                    rank,
                    courseSlots,
                    scored.attendanceDays(),
                    scored.combination().totalCredit(),
                    scored.totalFreeMinutes(),
                    scored.score()));
            rank++;
        }
        return results;
    }
}
