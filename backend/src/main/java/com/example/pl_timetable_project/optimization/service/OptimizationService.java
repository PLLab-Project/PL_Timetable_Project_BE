package com.example.pl_timetable_project.optimization.service;

import com.example.pl_timetable_project.academic.section.AcademicSection;
import com.example.pl_timetable_project.academic.section.AcademicSectionQueryRepository;
import com.example.pl_timetable_project.academic.section.SectionReference;
import com.example.pl_timetable_project.exception.ApplicationException;
import com.example.pl_timetable_project.exception.ForbiddenException;
import com.example.pl_timetable_project.exception.InvalidOptimizationConditionException;
import com.example.pl_timetable_project.exception.NoFeasibleTimetableException;
import com.example.pl_timetable_project.exception.OptimizationFailedException;
import com.example.pl_timetable_project.exception.OptimizationTimeoutException;
import com.example.pl_timetable_project.exception.TimetableNotFoundException;
import com.example.pl_timetable_project.optimization.algorithm.CandidateCourse;
import com.example.pl_timetable_project.optimization.algorithm.CandidateCourseFilter;
import com.example.pl_timetable_project.optimization.algorithm.CourseTimeSlot;
import com.example.pl_timetable_project.optimization.algorithm.CreditUnits;
import com.example.pl_timetable_project.optimization.algorithm.OptimizationConstraints;
import com.example.pl_timetable_project.optimization.algorithm.RequiredCoursePlacer;
import com.example.pl_timetable_project.optimization.algorithm.RequiredPlacementResult;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleCombination;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleScorer;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleSearchService;
import com.example.pl_timetable_project.optimization.algorithm.ScoredCombination;
import com.example.pl_timetable_project.optimization.dto.request.CourseCandidateRequest;
import com.example.pl_timetable_project.optimization.dto.request.OptimizationCreateRequest;
import com.example.pl_timetable_project.optimization.dto.request.TimeRangeRequest;
import com.example.pl_timetable_project.optimization.dto.response.OptimizationJobResponse;
import com.example.pl_timetable_project.optimization.entity.CourseSlot;
import com.example.pl_timetable_project.optimization.entity.OptimizationJob;
import com.example.pl_timetable_project.optimization.entity.OptimizationResult;
import com.example.pl_timetable_project.timetable.entity.Timetable;
import com.example.pl_timetable_project.timetable.repository.TimetableRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@Slf4j
public class OptimizationService {

    private static final long SEARCH_TIME_LIMIT_MILLIS = 10_000;
    private static final int MAX_CANDIDATE_SECTIONS = 100;

    private final OptimizationJobLifecycleService lifecycleService;
    private final TimetableRepository timetableRepository;
    private final AcademicSectionQueryRepository sectionQueryRepository;
    private final CandidateCourseFilter candidateCourseFilter;
    private final RequiredCoursePlacer requiredCoursePlacer;
    private final ScheduleSearchService scheduleSearchService;
    private final ScheduleScorer scheduleScorer;

    public OptimizationService(
            OptimizationJobLifecycleService lifecycleService,
            TimetableRepository timetableRepository,
            AcademicSectionQueryRepository sectionQueryRepository,
            CandidateCourseFilter candidateCourseFilter,
            RequiredCoursePlacer requiredCoursePlacer,
            ScheduleSearchService scheduleSearchService,
            ScheduleScorer scheduleScorer) {
        this.lifecycleService = lifecycleService;
        this.timetableRepository = timetableRepository;
        this.sectionQueryRepository = sectionQueryRepository;
        this.candidateCourseFilter = candidateCourseFilter;
        this.requiredCoursePlacer = requiredCoursePlacer;
        this.scheduleSearchService = scheduleSearchService;
        this.scheduleScorer = scheduleScorer;
    }

    public OptimizationJobResponse createJob(
            UUID userId, OptimizationCreateRequest request) {
        validateRequest(request);
        Timetable timetable = getOwnedTimetable(userId, request.getTimetableId());
        List<CandidateCourse> candidates =
                loadCandidates(timetable.getSemesterId(), request.getCandidateCourses());
        OptimizationConstraints constraints = buildConstraints(request, candidates);

        List<CandidateCourse> filtered = candidateCourseFilter.filter(candidates, constraints);
        requiredCoursePlacer.place(filtered, constraints.requiredSections());

        OptimizationJob job = lifecycleService.createPendingJobAndPublish(
                userId, timetable.getSemesterId(), request, candidates, constraints);
        return OptimizationJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public OptimizationJobResponse getJob(UUID userId, Long jobId) {
        return OptimizationJobResponse.from(lifecycleService.getOwnedJob(userId, jobId));
    }

    public void cancelJob(UUID userId, Long jobId) {
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
            List<CandidateCourse> filtered =
                    candidateCourseFilter.filter(event.candidates(), event.constraints());
            RequiredPlacementResult placement =
                    requiredCoursePlacer.place(
                            filtered, event.constraints().requiredSections());
            List<ScheduleCombination> combinations = scheduleSearchService.search(
                    placement.requiredCourses(),
                    placement.optionalCandidates(),
                    event.constraints());
            if (combinations.isEmpty()) {
                throw new NoFeasibleTimetableException(
                        "조건에 맞는 시간표 조합을 찾지 못했습니다. jobId=" + jobId);
            }

            List<ScoredCombination> scored = combinations.stream()
                    .map(combination -> scheduleScorer.score(
                            combination, event.constraints()))
                    .toList();
            lifecycleService.finalizeSuccess(jobId, toOptimizationResults(scored));
        } catch (OptimizationTimeoutException exception) {
            lifecycleService.finalizeTimeout(jobId, exception.getMessage());
        } catch (ApplicationException exception) {
            lifecycleService.finalizeFailed(jobId, exception.getMessage());
        } catch (Exception exception) {
            log.error("시간표 자동 편성 중 오류가 발생했습니다. jobId={}", jobId, exception);
            OptimizationFailedException failure =
                    new OptimizationFailedException("시간표 편성 중 오류가 발생했습니다.", exception);
            lifecycleService.finalizeFailed(jobId, failure.getMessage());
        }
    }

    private Timetable getOwnedTimetable(UUID userId, Long timetableId) {
        Timetable timetable = timetableRepository.findById(timetableId)
                .orElseThrow(() -> new TimetableNotFoundException(timetableId));
        if (userId == null || !timetable.getUserId().equals(userId)) {
            throw new ForbiddenException("해당 시간표에 접근할 권한이 없습니다. id=" + timetableId);
        }
        return timetable;
    }

    private List<CandidateCourse> loadCandidates(
            String semesterId, List<CourseCandidateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new InvalidOptimizationConditionException(
                    "후보 분반을 한 개 이상 입력해야 합니다.");
        }
        if (requests.size() > MAX_CANDIDATE_SECTIONS) {
            throw new InvalidOptimizationConditionException(
                    "후보 분반은 최대 " + MAX_CANDIDATE_SECTIONS + "개까지 요청할 수 있습니다.");
        }

        Map<SectionReference, AcademicSection> catalog =
                sectionQueryRepository.findBySemesterId(semesterId);
        Set<SectionReference> seen = new HashSet<>();
        List<CandidateCourse> candidates = new ArrayList<>();

        for (CourseCandidateRequest request : requests) {
            SectionReference reference = new SectionReference(
                    semesterId, request.getCourseCode(), request.getSectionCode());
            if (!seen.add(reference)) {
                throw new InvalidOptimizationConditionException(
                        "후보 분반이 중복됐습니다: " + reference.displayKey());
            }
            AcademicSection academicSection = catalog.get(reference);
            if (academicSection == null) {
                throw new InvalidOptimizationConditionException(
                        "학사 DB에 존재하지 않는 후보 분반입니다: " + reference.displayKey());
            }
            if (academicSection.meetings().isEmpty()) {
                throw new InvalidOptimizationConditionException(
                        "수업시간 미정 분반은 자동 편성 후보로 사용할 수 없습니다: "
                                + reference.displayKey());
            }
            candidates.add(new CandidateCourse(
                    reference,
                    academicSection.courseName(),
                    academicSection.professorName(),
                    CreditUnits.toUnits(academicSection.credits()),
                    request.isRequired(),
                    academicSection.meetings().stream()
                            .map(meeting -> new CourseTimeSlot(
                                    meeting.dayOfWeek(),
                                    meeting.startTime(),
                                    meeting.endTime()))
                            .toList()));
        }
        return candidates;
    }

    private OptimizationConstraints buildConstraints(
            OptimizationCreateRequest request, List<CandidateCourse> candidates) {
        Set<SectionReference> requiredSections = candidates.stream()
                .filter(CandidateCourse::required)
                .map(CandidateCourse::section)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new OptimizationConstraints(
                CreditUnits.toUnits(request.getMinCredits()),
                CreditUnits.toUnits(request.getMaxCredits()),
                CreditUnits.toUnits(request.getTargetCredits()),
                request.getExcludedDays() == null
                        ? Set.of() : Set.copyOf(request.getExcludedDays()),
                requiredSections,
                request.getAvailableTime().getStartTime(),
                request.getAvailableTime().getEndTime(),
                request.getLunchTime().getStartTime(),
                request.getLunchTime().getEndTime(),
                request.getMaxDailyClassMinutes(),
                SEARCH_TIME_LIMIT_MILLIS);
    }

    private void validateRequest(OptimizationCreateRequest request) {
        if (request.getAvailableTime() == null || request.getLunchTime() == null) {
            throw new InvalidOptimizationConditionException(
                    "수업 가능 시간과 점심시간은 필수입니다.");
        }
        if (request.getMinCredits().compareTo(request.getMaxCredits()) > 0) {
            throw new InvalidOptimizationConditionException(
                    "최소학점은 최대학점보다 클 수 없습니다.");
        }
        if (request.getTargetCredits().compareTo(request.getMinCredits()) < 0
                || request.getTargetCredits().compareTo(request.getMaxCredits()) > 0) {
            throw new InvalidOptimizationConditionException(
                    "목표학점은 최소학점과 최대학점 사이여야 합니다.");
        }
        validateTimeRange(request.getAvailableTime(), "수업 가능 시간");
        validateTimeRange(request.getLunchTime(), "점심시간");
        try {
            CreditUnits.toUnits(request.getMinCredits());
            CreditUnits.toUnits(request.getMaxCredits());
            CreditUnits.toUnits(request.getTargetCredits());
        } catch (ArithmeticException exception) {
            throw new InvalidOptimizationConditionException(
                    "학점은 소수점 둘째 자리까지만 입력할 수 있습니다.");
        }
    }

    private void validateTimeRange(TimeRangeRequest range, String label) {
        if (!range.getStartTime().isBefore(range.getEndTime())) {
            throw new InvalidOptimizationConditionException(
                    label + "의 시작 시각은 종료 시각보다 빨라야 합니다.");
        }
    }

    private List<OptimizationResult> toOptimizationResults(
            List<ScoredCombination> topCombinations) {
        List<OptimizationResult> results = new ArrayList<>();
        int rank = 1;
        for (ScoredCombination scored : topCombinations) {
            List<CourseSlot> courseSlots = scored.combination().courses().stream()
                    .flatMap(course -> course.timeSlots().stream()
                            .map(slot -> new CourseSlot(
                                    course.section(),
                                    course.courseName(),
                                    course.professorName(),
                                    CreditUnits.toCredits(course.creditUnits()),
                                    slot.dayOfWeek(),
                                    slot.startTime(),
                                    slot.endTime())))
                    .toList();
            results.add(new OptimizationResult(
                    rank++,
                    courseSlots,
                    scored.attendanceDays(),
                    CreditUnits.toCredits(scored.combination().totalCreditUnits()),
                    scored.totalFreeMinutes(),
                    scored.score()));
        }
        return results;
    }
}
