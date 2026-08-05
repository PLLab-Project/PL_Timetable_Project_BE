package com.example.pl_timetable_project.optimization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.pl_timetable_project.academic.course.CourseSequenceHintService;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Evaluation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Recommendation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourse;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourseGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.SecondaryMajorEvaluation;
import com.example.pl_timetable_project.academic.graduation.GraduationService;
import com.example.pl_timetable_project.academic.section.AcademicMeeting;
import com.example.pl_timetable_project.academic.section.AcademicSection;
import com.example.pl_timetable_project.academic.section.AcademicSectionQueryRepository;
import com.example.pl_timetable_project.academic.section.SectionReference;
import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import com.example.pl_timetable_project.completedcourse.entity.CompletedCourse;
import com.example.pl_timetable_project.completedcourse.repository.CompletedCourseRepository;
import com.example.pl_timetable_project.exception.AlreadyCompletedCourseException;
import com.example.pl_timetable_project.exception.InvalidAcademicQueryException;
import com.example.pl_timetable_project.exception.InvalidOptimizationConditionException;
import com.example.pl_timetable_project.optimization.algorithm.CandidateCourseFilter;
import com.example.pl_timetable_project.optimization.algorithm.OptimizationConstraints;
import com.example.pl_timetable_project.optimization.algorithm.RequiredCoursePlacer;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleScorer;
import com.example.pl_timetable_project.optimization.algorithm.ScheduleSearchService;
import com.example.pl_timetable_project.optimization.dto.request.CourseCandidateRequest;
import com.example.pl_timetable_project.optimization.dto.request.OptimizationCreateRequest;
import com.example.pl_timetable_project.optimization.dto.request.TimeRangeRequest;
import com.example.pl_timetable_project.optimization.entity.OptimizationJob;
import com.example.pl_timetable_project.optimization.entity.OptimizationJobStatus;
import com.example.pl_timetable_project.timetable.entity.Timetable;
import com.example.pl_timetable_project.timetable.repository.TimetableRepository;
import com.example.pl_timetable_project.timetable.service.TimetableService;
import com.example.pl_timetable_project.user.entity.StudentProfile;
import com.example.pl_timetable_project.user.repository.StudentAcademicProgramRepository;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OptimizationServiceTest {

    private static final String SEMESTER_ID = "2026-2";
    private static final long TIMETABLE_ID = 10L;

    private OptimizationJobLifecycleService lifecycleService;
    private TimetableRepository timetableRepository;
    private AcademicSectionQueryRepository sectionQueryRepository;
    private StudentProfileRepository studentProfileRepository;
    private StudentAcademicProgramRepository academicProgramRepository;
    private GraduationService graduationService;
    private CompletedCourseRepository completedCourseRepository;
    private CourseSequenceHintService courseSequenceHintService;
    private OptimizationService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        lifecycleService = mock(OptimizationJobLifecycleService.class);
        timetableRepository = mock(TimetableRepository.class);
        sectionQueryRepository = mock(AcademicSectionQueryRepository.class);
        studentProfileRepository = mock(StudentProfileRepository.class);
        academicProgramRepository = mock(StudentAcademicProgramRepository.class);
        graduationService = mock(GraduationService.class);
        completedCourseRepository = mock(CompletedCourseRepository.class);
        courseSequenceHintService = mock(CourseSequenceHintService.class);
        service = new OptimizationService(
                lifecycleService,
                timetableRepository,
                sectionQueryRepository,
                studentProfileRepository,
                academicProgramRepository,
                graduationService,
                completedCourseRepository,
                courseSequenceHintService,
                new CandidateCourseFilter(),
                new RequiredCoursePlacer(),
                mock(ScheduleSearchService.class),
                mock(ScheduleScorer.class),
                mock(TimetableService.class));

        userId = UUID.randomUUID();
        when(timetableRepository.findById(TIMETABLE_ID))
                .thenReturn(Optional.of(new Timetable(userId, SEMESTER_ID, "자동편성")));
        // 이 테스트들은 "수업시간 미정 분반 스킵" 시나리오만 검증하므로 학과 필터와는
        // 무관하다. 임의의 학과를 가진 학생으로 두고, 후보 강의는 학과 제한이 없는
        // 공통 강의로 만들어 학과 필터가 결과에 영향을 주지 않게 한다.
        StudentProfile profile = new StudentProfile(userId, "20260001");
        profile.update((short) 3, "D1", 2026, "REGULAR", "ADVANCED_MAJOR", null);
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        // 기본값은 "student_academic_programs에 선언된 학과 없음" — 이 경우
        // StudentProfile.academicUnitCode()(D1)로 폴백한다. 복수전공 시나리오를
        // 다루는 테스트만 이 스텁을 덮어쓴다.
        when(academicProgramRepository.findMajorAcademicUnitCodes(any())).thenReturn(List.of());
        // 기본값은 "이수한 과목 없음" — 이수과목 제외 시나리오를 다루는 테스트만
        // 이 스텁을 덮어써서 특정 과목이 이미 이수한 것처럼 만든다.
        when(completedCourseRepository.findAllByUserIdAndStatusIn(any(), any()))
                .thenReturn(List.of());
        // 기본값은 "선수과목 힌트 없음" — 힌트를 다루는 테스트만 이 스텁을 덮어쓴다.
        when(courseSequenceHintService.findPrerequisites(any())).thenReturn(Map.of());
        OptimizationJob job = pendingJob();
        when(lifecycleService.createPendingJobAndPublish(
                        any(), any(), any(), any(), any()))
                .thenReturn(job);
    }

    @Test
    void skipsOptionalCandidatesWhoseClassTimeIsToBeAnnounced() {
        SectionReference unscheduled = reference("004803");
        SectionReference scheduled = reference("922503");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                unscheduled, section(unscheduled, "현장실습I", List.of()),
                scheduled, section(scheduled, "세계전쟁사", List.of(new AcademicMeeting(
                        DayOfWeek.WEDNESDAY, LocalTime.of(13, 30), LocalTime.of(15, 30))))));
        OptimizationCreateRequest request = request(List.of(
                candidate("004803", false),
                candidate("922503", false)));

        var response = service.createJob(userId, request);

        assertThat(response.id()).isEqualTo(42L);
        verify(lifecycleService).createPendingJobAndPublish(
                eq(userId),
                eq(SEMESTER_ID),
                eq(request),
                argThat(candidates -> candidates.size() == 1
                        && candidates.get(0).section().equals(scheduled)),
                any());
    }

    @Test
    void rejectsRequiredCandidateWhoseClassTimeIsToBeAnnounced() {
        SectionReference unscheduled = reference("004803");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                unscheduled, section(unscheduled, "현장실습I", List.of())));

        assertThatThrownBy(() -> service.createJob(
                        userId, request(List.of(candidate("004803", true)))))
                .isInstanceOf(InvalidOptimizationConditionException.class)
                .hasMessageContaining("필수 분반")
                .hasMessageContaining("2026-2:004803:01");
        verify(lifecycleService, never()).createPendingJobAndPublish(
                any(), any(), any(), any(), any());
    }

    @Test
    void rejectsRequestWhenEveryExplicitCandidateIsUnscheduled() {
        SectionReference unscheduled = reference("004803");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                unscheduled, section(unscheduled, "현장실습I", List.of())));

        assertThatThrownBy(() -> service.createJob(
                        userId, request(List.of(candidate("004803", false)))))
                .isInstanceOf(InvalidOptimizationConditionException.class)
                .hasMessageContaining("자동편성 가능한 분반이 없습니다");
        verify(lifecycleService, never()).createPendingJobAndPublish(
                any(), any(), any(), any(), any());
    }

    @Test
    void excludesCompletedCourseFromServerGeneratedCandidates() {
        SectionReference completed = reference("004803");
        SectionReference notCompleted = reference("922503");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                completed, section(completed, "현장실습I", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)))),
                notCompleted, section(notCompleted, "세계전쟁사", List.of(new AcademicMeeting(
                        DayOfWeek.WEDNESDAY, LocalTime.of(13, 30), LocalTime.of(15, 30))))));
        stubCompletedCourses("004803");
        OptimizationCreateRequest request = request(List.of());

        service.createJob(userId, request);

        verify(lifecycleService).createPendingJobAndPublish(
                eq(userId),
                eq(SEMESTER_ID),
                eq(request),
                argThat(candidates -> candidates.size() == 1
                        && candidates.get(0).section().equals(notCompleted)),
                any());
    }

    @Test
    void queriesOnlyCompletedAndInProgressStatusesSoFailedCoursesRemainEligibleForRetake() {
        SectionReference scheduled = reference("004803");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                scheduled, section(scheduled, "현장실습I", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))));

        service.createJob(userId, request(List.of()));

        verify(completedCourseRepository).findAllByUserIdAndStatusIn(
                eq(userId),
                eq(List.of(CompletedCourseStatus.COMPLETED, CompletedCourseStatus.IN_PROGRESS)));
    }

    @Test
    void skipsOptionalExplicitCandidateThatIsAlreadyCompleted() {
        SectionReference completed = reference("004803");
        SectionReference notCompleted = reference("922503");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                completed, section(completed, "현장실습I", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)))),
                notCompleted, section(notCompleted, "세계전쟁사", List.of(new AcademicMeeting(
                        DayOfWeek.WEDNESDAY, LocalTime.of(13, 30), LocalTime.of(15, 30))))));
        stubCompletedCourses("004803");
        OptimizationCreateRequest request = request(List.of(
                candidate("004803", false),
                candidate("922503", false)));

        service.createJob(userId, request);

        verify(lifecycleService).createPendingJobAndPublish(
                eq(userId),
                eq(SEMESTER_ID),
                eq(request),
                argThat(candidates -> candidates.size() == 1
                        && candidates.get(0).section().equals(notCompleted)),
                any());
    }

    @Test
    void rejectsRequiredExplicitCandidateThatIsAlreadyCompleted() {
        SectionReference completed = reference("004803");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                completed, section(completed, "현장실습I", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))));
        stubCompletedCourses("004803");

        assertThatThrownBy(() -> service.createJob(
                        userId, request(List.of(candidate("004803", true)))))
                .isInstanceOf(AlreadyCompletedCourseException.class)
                .hasMessageContaining("2026-2:004803:01");
        verify(lifecycleService, never()).createPendingJobAndPublish(
                any(), any(), any(), any(), any());
    }

    @Test
    void resolvesUserAcademicUnitCodesFromDeclaredPrimaryAndDoubleMajorPrograms() {
        // student_academic_programs에 주전공(D1)·복수전공(D2)이 선언돼 있으면,
        // StudentProfile.academicUnitCode()(D1) 단일값이 아니라 이 둘을 모두 사용한다.
        when(academicProgramRepository.findMajorAcademicUnitCodes(userId))
                .thenReturn(List.of("D1", "D2"));
        SectionReference scheduled = reference("004803");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                scheduled, section(scheduled, "현장실습I", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))));

        service.createJob(userId, request(List.of()));

        verify(lifecycleService).createPendingJobAndPublish(
                any(), any(), any(), any(),
                argThat((OptimizationConstraints constraints) ->
                        constraints.userAcademicUnitCodes()
                                .containsAll(List.of("D1", "D2"))));
    }

    @Test
    void fallsBackToLegacyProfileAcademicUnitWhenNoProgramsAreDeclared() {
        // student_academic_programs가 비어 있는 레거시 계정은 setUp()의 기본 스텁대로
        // StudentProfile.academicUnitCode()(D1) 단일값으로 대체된다.
        SectionReference scheduled = reference("004803");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                scheduled, section(scheduled, "현장실습I", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))));

        service.createJob(userId, request(List.of()));

        verify(lifecycleService).createPendingJobAndPublish(
                any(), any(), any(), any(),
                argThat((OptimizationConstraints constraints) ->
                        constraints.userAcademicUnitCodes().equals(List.of("D1"))));
    }

    @Test
    void includesGraduationPriorityCourseCodesFromPrimaryAndSecondaryMajorWhenOptedIn() {
        SectionReference primaryPriority = reference("004803");
        SectionReference secondaryPriority = reference("922503");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                primaryPriority, section(primaryPriority, "전공필수1", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)))),
                secondaryPriority, section(secondaryPriority, "복수전공필수1", List.of(new AcademicMeeting(
                        DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))));
        when(graduationService.evaluate(userId, SEMESTER_ID)).thenReturn(
                evaluationWithPriorityCourses(
                        List.of("004803"),
                        List.of(),
                        secondaryEvaluationWithPriorityCourses(List.of("922503"), List.of())));

        service.createJob(userId, requestWithGraduationPriority(List.of()));

        verify(lifecycleService).createPendingJobAndPublish(
                any(), any(), any(), any(),
                argThat((OptimizationConstraints constraints) ->
                        constraints.graduationPriorityCourseCodes()
                                .containsAll(Set.of("004803", "922503"))));
    }

    @Test
    void excludesAlreadyCompletedCourseFromGraduationPriorityCourseCodes() {
        SectionReference completed = reference("004803");
        SectionReference notCompleted = reference("922503");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                completed, section(completed, "전공필수1", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)))),
                notCompleted, section(notCompleted, "전공필수2", List.of(new AcademicMeeting(
                        DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))));
        stubCompletedCourses("004803");
        when(graduationService.evaluate(userId, SEMESTER_ID)).thenReturn(
                evaluationWithPriorityCourses(List.of("004803", "922503"), List.of()));

        service.createJob(userId, requestWithGraduationPriority(List.of()));

        verify(lifecycleService).createPendingJobAndPublish(
                any(), any(), any(), any(),
                argThat((OptimizationConstraints constraints) ->
                        !constraints.graduationPriorityCourseCodes().contains("004803")
                                && constraints.graduationPriorityCourseCodes().contains("922503")));
    }

    @Test
    void excludesGraduationPriorityCourseCodesThatAreNotOfferedThisSemester() {
        SectionReference scheduled = reference("004803");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                scheduled, section(scheduled, "현장실습I", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))));
        when(graduationService.evaluate(userId, SEMESTER_ID)).thenReturn(
                evaluationWithPriorityCourses(List.of("NOT_OFFERED_THIS_SEMESTER"), List.of()));

        service.createJob(userId, requestWithGraduationPriority(List.of()));

        verify(lifecycleService).createPendingJobAndPublish(
                any(), any(), any(), any(),
                argThat((OptimizationConstraints constraints) ->
                        constraints.graduationPriorityCourseCodes().isEmpty()));
    }

    @Test
    void doesNotResolveGraduationPriorityCourseCodesWhenNotOptedIn() {
        // prioritizeGraduationRequirements 기본값은 false — 켜지 않으면 졸업요건
        // 조회 자체를 시도하지 않는다(불필요한 GraduationService 호출·실패 위험 없음).
        SectionReference scheduled = reference("004803");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                scheduled, section(scheduled, "현장실습I", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))));

        service.createJob(userId, request(List.of()));

        verify(graduationService, never()).evaluate(any(), any());
        verify(lifecycleService).createPendingJobAndPublish(
                any(), any(), any(), any(),
                argThat((OptimizationConstraints constraints) ->
                        constraints.graduationPriorityCourseCodes().isEmpty()));
    }

    @Test
    void fallsBackToEmptyGraduationPriorityCourseCodesWhenEvaluationFails() {
        // 옵트인 기능이라도 졸업요건 조회 자체가 실패하면(프로필 미완성 등) 자동편성
        // 본연의 기능은 항상 성공해야 한다 — 예외를 삼키고 빈 집합으로 대체한다.
        SectionReference scheduled = reference("004803");
        when(sectionQueryRepository.findBySemesterId(SEMESTER_ID)).thenReturn(Map.of(
                scheduled, section(scheduled, "현장실습I", List.of(new AcademicMeeting(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))))));
        when(graduationService.evaluate(userId, SEMESTER_ID))
                .thenThrow(new InvalidAcademicQueryException("학생 프로필이 완성되지 않았습니다."));

        var response = service.createJob(userId, requestWithGraduationPriority(List.of()));

        assertThat(response.id()).isEqualTo(42L);
        verify(lifecycleService).createPendingJobAndPublish(
                any(), any(), any(), any(),
                argThat((OptimizationConstraints constraints) ->
                        constraints.graduationPriorityCourseCodes().isEmpty()));
    }

    private OptimizationCreateRequest requestWithGraduationPriority(
            List<CourseCandidateRequest> candidates) {
        OptimizationCreateRequest request = request(candidates);
        org.springframework.test.util.ReflectionTestUtils.setField(
                request, "prioritizeGraduationRequirements", true);
        return request;
    }

    private Evaluation evaluationWithPriorityCourses(
            List<String> requiredCourseCodes, List<String> recommendedCourseCodes) {
        return evaluationWithPriorityCourses(requiredCourseCodes, recommendedCourseCodes, null);
    }

    private Evaluation evaluationWithPriorityCourses(
            List<String> requiredCourseCodes,
            List<String> recommendedCourseCodes,
            SecondaryMajorEvaluation secondaryMajor) {
        return new Evaluation(
                SEMESTER_ID,
                null,
                null,
                List.of(),
                List.of(),
                requiredCourseCodes.stream().map(this::requiredCourseGap).toList(),
                recommendedCourseCodes.stream().map(this::recommendation).toList(),
                false,
                List.of(),
                List.of(),
                List.of(),
                secondaryMajor);
    }

    private SecondaryMajorEvaluation secondaryEvaluationWithPriorityCourses(
            List<String> requiredCourseCodes, List<String> recommendedCourseCodes) {
        return new SecondaryMajorEvaluation(
                null,
                List.of(),
                requiredCourseCodes.stream().map(this::requiredCourseGap).toList(),
                recommendedCourseCodes.stream().map(this::recommendation).toList(),
                false,
                List.of(),
                List.of(),
                List.of());
    }

    private RequiredCourseGap requiredCourseGap(String courseCode) {
        return new RequiredCourseGap(new RequiredCourse(
                "MAJOR_REQUIRED", courseCode, "테스트필수과목",
                List.of(), BigDecimal.valueOf(3), null, null));
    }

    private Recommendation recommendation(String courseCode) {
        return new Recommendation(
                SEMESTER_ID, courseCode, "테스트추천과목", "전공필수",
                BigDecimal.valueOf(3), 1, List.of());
    }

    /**
     * completedCourseRepository를 스텁한다. mock()/when()을 다른 when(...).thenReturn(...)
     * 호출의 인자 자리에서 중첩 호출하면 Mockito의 스터빙 진행 상태가 꼬여
     * UnfinishedStubbingException이 나므로, 완성된 리스트를 먼저 만든 뒤 스텁한다.
     */
    private void stubCompletedCourses(String... courseCodes) {
        List<CompletedCourse> courses = new java.util.ArrayList<>();
        for (String courseCode : courseCodes) {
            CompletedCourse course = mock(CompletedCourse.class);
            when(course.getCourseCode()).thenReturn(courseCode);
            courses.add(course);
        }
        when(completedCourseRepository.findAllByUserIdAndStatusIn(any(), any()))
                .thenReturn(courses);
    }

    private OptimizationCreateRequest request(List<CourseCandidateRequest> candidates) {
        return new OptimizationCreateRequest(
                TIMETABLE_ID,
                BigDecimal.ZERO,
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(3),
                Set.of(),
                new TimeRangeRequest(LocalTime.of(8, 0), LocalTime.of(18, 0)),
                new TimeRangeRequest(LocalTime.NOON, LocalTime.of(13, 0)),
                480,
                candidates);
    }

    private CourseCandidateRequest candidate(String courseCode, boolean required) {
        return new CourseCandidateRequest(courseCode, "01", required);
    }

    private SectionReference reference(String courseCode) {
        return new SectionReference(SEMESTER_ID, courseCode, "01");
    }

    private AcademicSection section(
            SectionReference reference, String courseName, List<AcademicMeeting> meetings) {
        // 이 테스트 스위트는 학과 필터링과 무관한 시나리오만 다루므로 학과 제한이
        // 없는(공통) 강의로 취급한다.
        return new AcademicSection(
                reference, courseName, "담당교수", BigDecimal.valueOf(3), meetings, List.of(), null, null);
    }

    private OptimizationJob pendingJob() {
        OptimizationJob job = mock(OptimizationJob.class);
        when(job.getId()).thenReturn(42L);
        when(job.getUserId()).thenReturn(userId);
        when(job.getTimetableId()).thenReturn(TIMETABLE_ID);
        when(job.getSemesterId()).thenReturn(SEMESTER_ID);
        when(job.getStatus()).thenReturn(OptimizationJobStatus.PENDING);
        when(job.getResults()).thenReturn(List.of());
        when(job.getCreatedAt()).thenReturn(Instant.parse("2026-08-03T06:00:00Z"));
        return job;
    }
}
