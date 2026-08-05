package com.example.pl_timetable_project.optimization.service;

import com.example.pl_timetable_project.academic.course.CourseSequenceHintService;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Evaluation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Recommendation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourseGap;
import com.example.pl_timetable_project.academic.graduation.GraduationService;
import com.example.pl_timetable_project.academic.section.AcademicSection;
import com.example.pl_timetable_project.academic.section.AcademicSectionQueryRepository;
import com.example.pl_timetable_project.academic.section.SectionReference;
import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import com.example.pl_timetable_project.completedcourse.entity.CompletedCourse;
import com.example.pl_timetable_project.completedcourse.repository.CompletedCourseRepository;
import com.example.pl_timetable_project.exception.AlreadyCompletedCourseException;
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
import com.example.pl_timetable_project.user.repository.StudentAcademicProgramRepository;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final StudentAcademicProgramRepository academicProgramRepository;
    private final GraduationService graduationService;
    private final CompletedCourseRepository completedCourseRepository;
    private final CourseSequenceHintService courseSequenceHintService;
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
            StudentAcademicProgramRepository academicProgramRepository,
            GraduationService graduationService,
            CompletedCourseRepository completedCourseRepository,
            CourseSequenceHintService courseSequenceHintService,
            CandidateCourseFilter candidateCourseFilter,
            RequiredCoursePlacer requiredCoursePlacer,
            ScheduleSearchService scheduleSearchService,
            ScheduleScorer scheduleScorer,
            TimetableService timetableService) {
        this.lifecycleService = lifecycleService;
        this.timetableRepository = timetableRepository;
        this.sectionQueryRepository = sectionQueryRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.academicProgramRepository = academicProgramRepository;
        this.graduationService = graduationService;
        this.completedCourseRepository = completedCourseRepository;
        this.courseSequenceHintService = courseSequenceHintService;
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
        Set<String> completedCourseCodes = resolveCompletedCourseCodes(userId);
        // 교양 학점 상한(liberalCreditCap)은 옵트인이 아니라 항상 켜져 있으므로,
        // prioritizeGraduationRequirements와 무관하게 매번 졸업요건을 조회해 둔다.
        Evaluation graduationEvaluation =
                resolveGraduationEvaluation(userId, timetable.getSemesterId());
        Set<String> graduationPriorityCourseCodes = request.isPrioritizeGraduationRequirements()
                ? resolveGraduationPriorityCourseCodes(
                        graduationEvaluation, timetable.getSemesterId(), completedCourseCodes)
                : Set.of();
        Integer liberalCreditCap = resolveLiberalCreditCap(graduationEvaluation);
        List<CandidateCourse> candidates =
                loadCandidates(
                        timetable.getSemesterId(),
                        request.getCandidateCourses(),
                        request.getRequiredCourses(),
                        completedCourseCodes);
        OptimizationConstraints constraints =
                buildConstraints(
                        request,
                        candidates,
                        userAcademicUnitCodes,
                        completedCourseCodes,
                        graduationPriorityCourseCodes,
                        liberalCreditCap);

        List<CandidateCourse> filtered = candidateCourseFilter.filter(candidates, constraints);
        requiredCoursePlacer.place(filtered, constraints.requiredSections());

        OptimizationJob job = lifecycleService.createPendingJobAndPublish(
                userId, timetable.getSemesterId(), request, candidates, constraints);
        return OptimizationJobResponse.from(job);
    }

    /**
     * 로그인한 사용자가 속한 학과 코드 목록을 만든다. student_academic_programs의
     * PRIMARY·DOUBLE_MAJOR 학과들을 "본인 학과"로 취급해 본인 전공 가중치
     * (SAME_MAJOR_BONUS)와 타 전공 진짜 제한 배제(hardRestrictedAcademicUnitCode)에
     * 모두 사용한다 — 복수전공생은 두 학과 강의 전부에서 가중치를 받고, 두 학과 중
     * 하나에만 걸린 제한 강의도 배제되지 않는다(둘 다 리스트에 담기므로 나머지
     * 필터링·스코어링 로직은 수정 없이 그대로 동작한다).
     * student_academic_programs에 데이터가 없는 레거시 계정은
     * StudentProfile.academicUnitCode() 단일값으로 대체한다.
     */
    private List<String> resolveUserAcademicUnitCodes(UUID userId) {
        List<String> declaredAcademicUnitCodes =
                academicProgramRepository.findMajorAcademicUnitCodes(userId);
        if (!declaredAcademicUnitCodes.isEmpty()) {
            return declaredAcademicUnitCodes;
        }
        StudentProfile profile = studentProfileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return profile.academicUnitCode() == null
                ? List.of()
                : List.of(profile.academicUnitCode());
    }

    /**
     * 로그인한 사용자가 이미 이수했거나(COMPLETED) 수강 중인(IN_PROGRESS) 과목의
     * 코드 집합을 만든다. F학점 등으로 재수강이 가능한 과목(FAILED)과 수강철회
     * (WITHDRAWN)는 일부러 제외하지 않는다 — 졸업요건 추천(GraduationQueryRepository)의
     * 판정 기준과 동일하다. 과목코드가 없는 레거시/수동입력 이수 기록은 대소문자
     * 비교만으로는 매칭할 수 없어 이 집합에 담기지 않는다.
     */
    private Set<String> resolveCompletedCourseCodes(UUID userId) {
        return completedCourseRepository
                .findAllByUserIdAndStatusIn(
                        userId,
                        List.of(CompletedCourseStatus.COMPLETED, CompletedCourseStatus.IN_PROGRESS))
                .stream()
                .map(CompletedCourse::getCourseCode)
                .filter(java.util.Objects::nonNull)
                .map(courseCode -> courseCode.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
            Set<String> completedCourseCodes) {
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
            Map<SectionReference, AcademicSection> catalog =
                    sectionQueryRepository.findBySemesterId(semesterId);
            List<AcademicSection> eligibleSections = catalog.values().stream()
                    .filter(section -> !section.meetings().isEmpty())
                    .filter(section -> !isAlreadyCompleted(section.reference(), completedCourseCodes))
                    .toList();
            Map<String, List<String>> prerequisitesByCourseCode =
                    findPrerequisites(eligibleSections);
            List<CandidateCourse> serverCandidates = eligibleSections.stream()
                    .map(section -> toCandidateCourse(
                            section,
                            requiredSections.contains(section.reference()),
                            prerequisitesByCourseCode))
                    .toList();
            validateRequiredCandidates(requiredSections, serverCandidates);
            if (serverCandidates.isEmpty()) {
                throw new InvalidOptimizationConditionException(
                        "해당 학기에 자동편성 가능한 분반이 없습니다.");
            }
            return serverCandidates;
        }

        // 클라이언트가 후보 분반을 직접 지정한 경로이므로 전체 카탈로그에서 조회한다.
        Map<SectionReference, AcademicSection> catalog =
                sectionQueryRepository.findBySemesterId(semesterId);
        Map<String, List<String>> prerequisitesByCourseCode = courseSequenceHintService.findPrerequisites(
                requests.stream().map(CourseCandidateRequest::getCourseCode).distinct().toList());
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
            if (isAlreadyCompleted(reference, completedCourseCodes)) {
                if (requiredSections.contains(reference)) {
                    throw new AlreadyCompletedCourseException(
                            "이미 이수했거나 수강 중인 과목을 필수 강의로 지정했습니다: "
                                    + reference.displayKey());
                }
                continue;
            }
            if (academicSection.meetings().isEmpty()) {
                if (requiredSections.contains(reference)) {
                    throw new InvalidOptimizationConditionException(
                            "수업시간 미정 필수 분반은 자동 편성할 수 없습니다: "
                                    + reference.displayKey());
                }
                continue;
            }
            candidates.add(toCandidateCourse(
                    academicSection, requiredSections.contains(reference), prerequisitesByCourseCode));
        }
        validateRequiredCandidates(requiredSections, candidates);
        if (candidates.isEmpty()) {
            throw new InvalidOptimizationConditionException(
                    "자동편성 가능한 분반이 없습니다. 수업시간 미정 후보를 확인해 주세요.");
        }
        return candidates;
    }

    private Map<String, List<String>> findPrerequisites(List<AcademicSection> sections) {
        return courseSequenceHintService.findPrerequisites(
                sections.stream()
                        .map(section -> section.reference().getCourseCode())
                        .distinct()
                        .toList());
    }

    private boolean isAlreadyCompleted(
            SectionReference reference, Set<String> completedCourseCodes) {
        return completedCourseCodes.contains(
                reference.getCourseCode().toLowerCase(Locale.ROOT));
    }

    private SectionReference toReference(
            String semesterId, CourseCandidateRequest request) {
        return new SectionReference(
                semesterId, request.getCourseCode(), request.getSectionCode());
    }

    private CandidateCourse toCandidateCourse(
            AcademicSection academicSection,
            boolean required,
            Map<String, List<String>> prerequisitesByCourseCode) {
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
                academicSection.restrictedAcademicUnitCodes(),
                academicSection.liberalAreaCode(),
                prerequisitesByCourseCode.getOrDefault(
                        academicSection.reference().getCourseCode(), List.of()),
                academicSection.hardRestrictedAcademicUnitCode(),
                academicSection.liberalCredit());
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

    /**
     * 자동편성이 필요로 하는 졸업요건 조회(Evaluation)를 한 번만 시도해 두 기능
     * (졸업요건 우선배치·교양 학점 상한)이 공유한다. 실패해도(프로필 미완성,
     * 카탈로그 규칙 없음 등) 자동편성 본연의 기능은 항상 성공해야 하므로, 두
     * 기능 모두 이 메서드가 반환한 null을 "정보 없음"으로 안전하게 취급한다.
     */
    private Evaluation resolveGraduationEvaluation(UUID userId, String semesterId) {
        try {
            return graduationService.evaluate(userId, semesterId);
        } catch (ApplicationException notReady) {
            log.info(
                    "자동편성에서 졸업요건 조회를 건너뜁니다(우선배치·교양 학점 상한 모두 "
                            + "미적용). userId={}, semesterId={}, reason={}",
                    userId, semesterId, notReady.getMessage());
            return null;
        }
    }

    /**
     * 졸업요건상 부족한 필수과목(requiredCourseGaps)·추천과목(recommendations)의 과목
     * 코드를 모아, 이번 편성 대상 학기에 실제로 개설된 분반이 있는 것만 남긴다
     * (AcademicSectionQueryRepository로 확인). 복수전공이 있으면 그 학과 몫도 함께
     * 담는다. completedCourseCodes로 한 번 더 방어적으로 제외하지만, 애초에
     * GraduationService.evaluate()의 requiredCourseGaps/recommendations 자체가
     * 이미 이수·수강 중인 과목은 빼고 계산하므로 이중 방어에 가깝다.
     *
     * <p>이 기능은 옵트인(OptimizationCreateRequest.prioritizeGraduationRequirements)
     * 이다 — evaluation이 null이면(옵트인했지만 졸업요건 조회 자체가 실패한 경우)
     * 빈 집합으로 대체한다.</p>
     */
    private Set<String> resolveGraduationPriorityCourseCodes(
            Evaluation evaluation, String semesterId, Set<String> completedCourseCodes) {
        if (evaluation == null) {
            return Set.of();
        }
        Set<String> priorityCourseCodes = new HashSet<>();
        collectPriorityCourseCodes(
                priorityCourseCodes, evaluation.requiredCourseGaps(), evaluation.recommendations());
        if (evaluation.secondaryMajor() != null) {
            collectPriorityCourseCodes(
                    priorityCourseCodes,
                    evaluation.secondaryMajor().requiredCourseGaps(),
                    evaluation.secondaryMajor().recommendations());
        }
        priorityCourseCodes.removeIf(
                courseCode -> completedCourseCodes.contains(courseCode.toLowerCase(Locale.ROOT)));

        Set<String> offeredCourseCodes = sectionQueryRepository.findBySemesterId(semesterId)
                .keySet().stream()
                .map(SectionReference::getCourseCode)
                .collect(java.util.stream.Collectors.toSet());
        priorityCourseCodes.retainAll(offeredCourseCodes);
        return Set.copyOf(priorityCourseCodes);
    }

    private void collectPriorityCourseCodes(
            Set<String> target,
            List<RequiredCourseGap> requiredCourseGaps,
            List<Recommendation> recommendations) {
        requiredCourseGaps.stream()
                .map(gap -> gap.course().courseCode())
                .filter(java.util.Objects::nonNull)
                .forEach(target::add);
        recommendations.stream()
                .map(Recommendation::courseCode)
                .filter(java.util.Objects::nonNull)
                .forEach(target::add);
    }

    /**
     * "이미 채운 교양 학점 + 이번에 새로 채울 교양 학점"이 liberalTotalMax를 넘지
     * 않도록, 이번 학기에 더 채울 수 있는 교양 학점 상한을 creditUnits(1/100 단위)로
     * 계산한다. liberalTotalMax가 없는 규칙(상한 자체가 없음)이거나 졸업요건 조회에
     * 실패했으면(evaluation == null) null — "상한 없음"을 뜻한다. 이미 상한을
     * 넘겨 이수한 경우 음수가 아니라 0으로 clamp한다(추가로는 못 채우지만, 이미
     * 편성된/이수한 강의를 소급해서 배제하지는 않는다).
     *
     * <p>이 기능은 옵트인이 아니라 항상 켜둔다 — 하드 제약이 아니라 소프트
     * 페널티(ScheduleSearchService/ScheduleScorer)라 상한을 넘는 조합도 여전히
     * 나올 수 있고, 실패 시 안전하게 무시되므로 항상 계산해도 위험이 없다.</p>
     */
    private Integer resolveLiberalCreditCap(Evaluation evaluation) {
        if (evaluation == null || evaluation.rule() == null) {
            return null;
        }
        Integer liberalTotalMax = evaluation.rule().liberalArts().totalMaximum();
        if (liberalTotalMax == null) {
            return null;
        }
        java.math.BigDecimal remaining = java.math.BigDecimal.valueOf(liberalTotalMax)
                .subtract(evaluation.completedCredits().liberalTotal())
                .max(java.math.BigDecimal.ZERO)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        return CreditUnits.toUnits(remaining);
    }

    private OptimizationConstraints buildConstraints(
            OptimizationCreateRequest request,
            List<CandidateCourse> candidates,
            List<String> userAcademicUnitCodes,
            Set<String> completedCourseCodes,
            Set<String> graduationPriorityCourseCodes,
            Integer liberalCreditCap) {
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
                userAcademicUnitCodes,
                request.getSelectedLiberalAreas() == null
                        ? List.of() : List.copyOf(request.getSelectedLiberalAreas()),
                completedCourseCodes,
                graduationPriorityCourseCodes,
                liberalCreditCap);
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
