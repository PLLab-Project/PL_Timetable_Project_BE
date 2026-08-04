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
import com.example.pl_timetable_project.academic.section.AcademicMeeting;
import com.example.pl_timetable_project.academic.section.AcademicSection;
import com.example.pl_timetable_project.academic.section.AcademicSectionQueryRepository;
import com.example.pl_timetable_project.academic.section.SectionReference;
import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import com.example.pl_timetable_project.completedcourse.entity.CompletedCourse;
import com.example.pl_timetable_project.completedcourse.repository.CompletedCourseRepository;
import com.example.pl_timetable_project.exception.AlreadyCompletedCourseException;
import com.example.pl_timetable_project.exception.InvalidOptimizationConditionException;
import com.example.pl_timetable_project.optimization.algorithm.CandidateCourseFilter;
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
        completedCourseRepository = mock(CompletedCourseRepository.class);
        courseSequenceHintService = mock(CourseSequenceHintService.class);
        service = new OptimizationService(
                lifecycleService,
                timetableRepository,
                sectionQueryRepository,
                studentProfileRepository,
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
