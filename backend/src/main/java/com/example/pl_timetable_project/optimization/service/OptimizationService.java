package com.example.pl_timetable_project.optimization.service;

import com.example.pl_timetable_project.academic.section.AcademicSection;
import com.example.pl_timetable_project.academic.section.AcademicSectionQueryRepository;
import com.example.pl_timetable_project.academic.section.SectionReference;
import com.example.pl_timetable_project.common.exception.BusinessException;
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
import com.example.pl_timetable_project.optimization.algorithm.OptimizationTimeRange;
import com.example.pl_timetable_project.optimization.algorithm.RequiredCoursePlacer;
import com.example.pl_timetable_project.optimization.algorithm.RequiredPlacementResult;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleCombination;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleScorer;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleSearchService;
import com.example.pl_timetable_project.optimization.algorithm.ScoredCombination;
import com.example.pl_timetable_project.optimization.dto.request.CourseCandidateRequest;
import com.example.pl_timetable_project.optimization.dto.request.BlockedTimeRequest;
import com.example.pl_timetable_project.optimization.dto.request.OptimizationCreateRequest;
import com.example.pl_timetable_project.optimization.dto.request.TimeRangeRequest;
import com.example.pl_timetable_project.optimization.dto.response.OptimizationJobResponse;
import com.example.pl_timetable_project.optimization.entity.CourseSlot;
import com.example.pl_timetable_project.optimization.entity.OptimizationJob;
import com.example.pl_timetable_project.optimization.entity.OptimizationResult;
import com.example.pl_timetable_project.timetable.entity.Timetable;
import com.example.pl_timetable_project.timetable.repository.TimetableRepository;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCourseRequest;
import com.example.pl_timetable_project.timetable.dto.response.TimetableResponse;
import com.example.pl_timetable_project.timetable.service.TimetableService;
import com.example.pl_timetable_project.user.UserErrorCode;
import com.example.pl_timetable_project.user.entity.StudentProfile;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    private final OptimizationJobLifecycleService lifecycleService;
    private final TimetableRepository timetableRepository;
    private final AcademicSectionQueryRepository sectionQueryRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CandidateCourseFilter candidateCourseFilter;
    private final RequiredCoursePlacer requiredCoursePlacer;
    private final ScheduleSearchService scheduleSearchService;
    private final ScheduleScorer scheduleScorer;
    private final TimetableService timetableService;

    public OptimizationService(
            OptimizationJobLifecycleService lifecycleService,
            TimetableRepository timetableRepository,
            AcademicSectionQueryRepository sectionQueryRepository,
            StudentProfileRepository studentProfileRepository,
            CandidateCourseFilter candidateCourseFilter,
            RequiredCoursePlacer requiredCoursePlacer,
            ScheduleSearchService scheduleSearchService,
            ScheduleScorer scheduleScorer,
            TimetableService timetableService) {
        this.lifecycleService = lifecycleService;
        this.timetableRepository = timetableRepository;
        this.sectionQueryRepository = sectionQueryRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.candidateCourseFilter = candidateCourseFilter;
        this.requiredCoursePlacer = requiredCoursePlacer;
        this.scheduleSearchService = scheduleSearchService;
        this.scheduleScorer = scheduleScorer;
        this.timetableService = timetableService;
    }

    public OptimizationJobResponse createJob(
            UUID userId, OptimizationCreateRequest request) {
        validateRequest(request);
        Timetable timetable = getOwnedTimetable(userId, request.getTimetableId());
        List<String> userAcademicUnitCodes = resolveUserAcademicUnitCodes(userId);
        List<CandidateCourse> candidates =
                loadCandidates(
                        timetable.getSemesterId(),
                        request.getCandidateCourses(),
                        request.getRequiredCourses(),
                        userAcademicUnitCodes);
        OptimizationConstraints constraints =
                buildConstraints(request, candidates, userAcademicUnitCodes);

        List<CandidateCourse> filtered = candidateCourseFilter.filter(candidates, constraints);
        requiredCoursePlacer.place(filtered, constraints.requiredSections());

        OptimizationJob job = lifecycleService.createPendingJobAndPublish(
                userId, timetable.getSemesterId(), request, candidates, constraints);
        return OptimizationJobResponse.from(job);
    }

    /**
     * 로그인한 사용자가 속한 학과 코드 목록을 만든다. 지금은 StudentProfile에 학과가
     * 하나뿐이라 리스트에 최대 한 건만 담기지만, 복수전공으로 두 번째 학과 필드가
     * 추가되면 이 메서드만 확장하면 나머지 후보 필터링 로직은 그대로 재사용된다.
     */
    private List<String> resolveUserAcademicUnitCodes(UUID userId) {
        StudentProfile profile = studentProfileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return resolveUserAcademicUnitCodes(profile);
    }

    private List<String> resolveUserAcademicUnitCodes(StudentProfile profile) {
        return profile.academicUnitCode() == null
                ? List.of()
                : List.of(profile.academicUnitCode());
    }

    @Transactional(readOnly = true)
    public OptimizationJobResponse getJob(UUID userId, Long jobId) {
        return OptimizationJobResponse.from(lifecycleService.getOwnedJob(userId, jobId));
    }

    public void cancelJob(UUID userId, Long jobId) {
        lifecycleService.cancel(userId, jobId);
    }

    @Transactional
    public TimetableResponse applyResult(UUID userId, Long jobId, int rank) {
        OptimizationJob job = lifecycleService.getOwnedJob(userId, jobId);
        if (job.getStatus()
                != com.example.pl_timetable_project.optimization.entity.OptimizationJobStatus.SUCCESS) {
            throw new InvalidOptimizationConditionException(
                    "성공한 자동편성 작업만 시간표에 적용할 수 있습니다.");
        }
        OptimizationResult result = job.getResults().stream()
                .filter(candidate -> candidate.getRank() == rank)
                .findFirst()
                .orElseThrow(() -> new InvalidOptimizationConditionException(
                        "해당 순위의 자동편성 결과가 없습니다. rank=" + rank));
        Set<SectionReference> sections = result.getCourseSlots().stream()
                .map(CourseSlot::getSection)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<TimetableCourseRequest> requests = sections.stream()
                .map(section -> new TimetableCourseRequest(
                        section.getCourseCode(), section.getSectionCode()))
                .toList();
        return timetableService.updateSections(
                userId, job.getTimetableId(), requests);
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
            String semesterId,
            List<CourseCandidateRequest> requests,
            List<CourseCandidateRequest> requiredRequests,
            List<String> userAcademicUnitCodes) {
        Set<SectionReference> requiredSections = new HashSet<>();
        if (requiredRequests != null) {
            requiredRequests.forEach(request -> requiredSections.add(
                    toReference(semesterId, request)));
        }
        if (requests != null) {
            requests.stream()
                    .filter(CourseCandidateRequest::isRequired)
                    .map(request -> toReference(semesterId, request))
                    .forEach(requiredSections::add);
        }

        if (requests == null || requests.isEmpty()) {
            // 서버가 후보를 자동 생성하는 경로이므로 학과 필터를 DB 조회 단계에서 바로 적용한다.
            Map<SectionReference, AcademicSection> catalog =
                    sectionQueryRepository.findBySemesterId(semesterId, userAcademicUnitCodes);
            List<CandidateCourse> serverCandidates = catalog.values().stream()
                    .filter(section -> !section.meetings().isEmpty())
                    .map(section -> toCandidateCourse(
                            section,
                            requiredSections.contains(section.reference())))
                    .toList();
            validateRequiredCandidates(requiredSections, serverCandidates);
            if (serverCandidates.isEmpty()) {
                throw new InvalidOptimizationConditionException(
                        "해당 학기에 자동편성 가능한 분반이 없습니다.");
            }
            return serverCandidates;
        }

        // 클라이언트가 후보 분반을 직접 지정한 경로이므로, "존재하지 않는 분반" 오류와
        // "학과가 맞지 않는 분반"을 구분할 수 있도록 학과 필터 없이 전체 카탈로그에서 조회한다.
        // 학과 필터는 이후 CandidateCourseFilter가 리스트 기반으로 동일하게 적용한다.
        Map<SectionReference, AcademicSection> catalog =
                sectionQueryRepository.findBySemesterId(semesterId);
        Set<SectionReference> seen = new HashSet<>();
        List<CandidateCourse> candidates = new ArrayList<>();

        for (CourseCandidateRequest request : requests) {
            SectionReference reference = toReference(semesterId, request);
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
            candidates.add(toCandidateCourse(
                    academicSection, requiredSections.contains(reference)));
        }
        validateRequiredCandidates(requiredSections, candidates);
        return candidates;
    }

    private SectionReference toReference(
            String semesterId, CourseCandidateRequest request) {
        return new SectionReference(
                semesterId, request.getCourseCode(), request.getSectionCode());
    }

    private CandidateCourse toCandidateCourse(
            AcademicSection academicSection, boolean required) {
        return new CandidateCourse(
                academicSection.reference(),
                academicSection.courseName(),
                academicSection.professorName(),
                CreditUnits.toUnits(academicSection.credits()),
                required,
                academicSection.meetings().stream()
                        .map(meeting -> new CourseTimeSlot(
                                meeting.dayOfWeek(),
                                meeting.startTime(),
                                meeting.endTime()))
                        .toList(),
                academicSection.restrictedAcademicUnitCodes());
    }

    private void validateRequiredCandidates(
            Set<SectionReference> requiredSections,
            List<CandidateCourse> candidates) {
        Set<SectionReference> available = candidates.stream()
                .map(CandidateCourse::section)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> missing = requiredSections.stream()
                .filter(section -> !available.contains(section))
                .map(SectionReference::displayKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!missing.isEmpty()) {
            throw new InvalidOptimizationConditionException(
                    "필수 분반이 없거나 수업시간이 미정입니다. sections=" + missing);
        }
    }

    private OptimizationConstraints buildConstraints(
            OptimizationCreateRequest request,
            List<CandidateCourse> candidates,
            List<String> userAcademicUnitCodes) {
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
                effectiveAvailableTimes(request).stream()
                        .map(range -> new OptimizationTimeRange(
                                range.getStartTime(), range.getEndTime()))
                        .toList(),
                request.getBlockedTimes() == null
                        ? List.of()
                        : request.getBlockedTimes().stream()
                                .map(range -> new CourseTimeSlot(
                                        range.dayOfWeek(),
                                        range.startTime(),
                                        range.endTime()))
                                .toList(),
                request.getLunchTime().getStartTime(),
                request.getLunchTime().getEndTime(),
                request.getMaxDailyClassMinutes(),
                SEARCH_TIME_LIMIT_MILLIS,
                userAcademicUnitCodes);
    }

    private void validateRequest(OptimizationCreateRequest request) {
        if (request.getLunchTime() == null || effectiveAvailableTimes(request).isEmpty()) {
            throw new InvalidOptimizationConditionException(
                    "수업 가능 시간은 한 개 이상이고 점심시간은 필수입니다.");
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
        List<TimeRangeRequest> availableTimes = effectiveAvailableTimes(request);
        for (int index = 0; index < availableTimes.size(); index++) {
            validateTimeRange(availableTimes.get(index), "수업 가능 시간[" + index + "]");
        }
        if (request.getBlockedTimes() != null) {
            for (int index = 0; index < request.getBlockedTimes().size(); index++) {
                validateBlockedTime(
                        request.getBlockedTimes().get(index), index);
            }
        }
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

    private void validateBlockedTime(BlockedTimeRequest range, int index) {
        if (range.dayOfWeek() == null
                || range.startTime() == null
                || range.endTime() == null
                || !range.startTime().isBefore(range.endTime())) {
            throw new InvalidOptimizationConditionException(
                    "blockedTimes[" + index + "]의 요일과 올바른 시작·종료 시각이 필요합니다.");
        }
    }

    private List<TimeRangeRequest> effectiveAvailableTimes(
            OptimizationCreateRequest request) {
        if (request.getAvailableTimes() != null
                && !request.getAvailableTimes().isEmpty()) {
            return request.getAvailableTimes();
        }
        return request.getAvailableTime() == null
                ? List.of() : List.of(request.getAvailableTime());
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
