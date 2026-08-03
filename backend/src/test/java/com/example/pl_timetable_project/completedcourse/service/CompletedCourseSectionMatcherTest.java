package com.example.pl_timetable_project.completedcourse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.pl_timetable_project.academic.course.dto.CourseSessionResponse;
import com.example.pl_timetable_project.academic.semester.SemesterQueryRepository;
import com.example.pl_timetable_project.academic.semester.dto.SemesterResponse;
import com.example.pl_timetable_project.completedcourse.CompletedCourseGradingBasis;
import com.example.pl_timetable_project.completedcourse.dto.OcrCourseMatchStatus;
import com.example.pl_timetable_project.completedcourse.dto.OcrDocumentType;
import com.example.pl_timetable_project.completedcourse.dto.RecognizedCourseMeetingResponse;
import com.example.pl_timetable_project.completedcourse.dto.RecognizedCourseResponse;
import com.example.pl_timetable_project.completedcourse.repository.CompletedCourseOcrMatchRepository;
import com.example.pl_timetable_project.completedcourse.repository.CompletedCourseOcrMatchRepository.SectionCandidate;
import com.example.pl_timetable_project.user.entity.StudentProfile;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompletedCourseSectionMatcherTest {

    private final CompletedCourseOcrMatchRepository matchRepository =
            mock(CompletedCourseOcrMatchRepository.class);
    private final SemesterQueryRepository semesterRepository =
            mock(SemesterQueryRepository.class);
    private final CompletedCourseSectionMatcher matcher =
            new CompletedCourseSectionMatcher(matchRepository, semesterRepository);

    @Test
    void selectsUniqueSectionUsingVisibleSemesterTimesAndRoom() {
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                candidate(
                        "2026-1",
                        "561103",
                        "01",
                        "자료구조",
                        "박정규",
                        session(DayOfWeek.MONDAY, "15:30", "17:30", "공다A 411"),
                        session(DayOfWeek.TUESDAY, "11:30", "13:30", "공다B 410")),
                candidate(
                        "2026-1",
                        "561103",
                        "02",
                        "자료구조",
                        "박정규",
                        session(DayOfWeek.WEDNESDAY, "13:30", "15:30", "공다B 401"),
                        session(DayOfWeek.THURSDAY, "13:30", "15:30", "공다A 414"))));

        var result = matcher.match(OcrDocumentType.TIMETABLE, "2026년 1학기", List.of(course(
                "자료구조",
                null,
                meeting(DayOfWeek.MONDAY, "15:30", "17:30", "공다A411"),
                meeting(DayOfWeek.TUESDAY, "11:30", "13:30", "공다B410"))));

        assertThat(result.resolvedSemester()).isEqualTo("2026-1");
        assertThat(result.courses()).singleElement().satisfies(course -> {
            assertThat(course.matchStatus()).isEqualTo(OcrCourseMatchStatus.MATCHED);
            assertThat(course.courseName()).isEqualTo("자료구조");
            assertThat(course.credits()).isEqualByComparingTo("3.0");
            assertThat(course.category()).isEqualTo("전공선택");
            assertThat(course.semester()).isEqualTo("2026-1");
            assertThat(course.matchedSection().courseCode()).isEqualTo("561103");
            assertThat(course.matchedSection().sectionCode()).isEqualTo("01");
            assertThat(course.matchedSection().matchScore())
                    .isEqualByComparingTo("0.80");
            assertThat(course.matchedSection().matchedEvidence())
                    .contains("COURSE_NAME", "SEMESTER", "MEETINGS_2_OF_2", "ROOMS_2_OF_2");
        });
    }

    @Test
    void infersSemesterOnlyAfterCrossSemesterSectionMatch() {
        when(semesterRepository.findAll(false)).thenReturn(List.of(
                semester("2026-2"), semester("2026-1")));
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                candidate(
                        "2026-1",
                        "927381",
                        "05",
                        "LCT(LearningbyCommunication＆Teamwork)",
                        "정소명",
                        session(DayOfWeek.THURSDAY, "11:30", "13:30", "인102")),
                candidate(
                        "2026-2",
                        "927381",
                        "08",
                        "LCT(LearningbyCommunication&Teamwork)",
                        "김승남",
                        session(DayOfWeek.THURSDAY, "11:30", "13:30", "인110-일반강의실"))));

        var result = matcher.match(OcrDocumentType.TIMETABLE, null, List.of(course(
                "LCT(Learning by Communication & Teamwork)",
                null,
                meeting(DayOfWeek.THURSDAY, "11:30", "13:30", "인110"))));

        assertThat(result.resolvedSemester()).isEqualTo("2026-2");
        assertThat(result.courses()).singleElement().satisfies(course -> {
            assertThat(course.semester()).isEqualTo("2026-2");
            assertThat(course.matchStatus()).isEqualTo(OcrCourseMatchStatus.MATCHED);
            assertThat(course.matchedSection().semesterId()).isEqualTo("2026-2");
            assertThat(course.matchedSection().sectionCode()).isEqualTo("08");
        });
    }

    @Test
    void identifiesCourseButNotSectionWhenOnlyNameIsAvailable() {
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                candidate("2026-1", "565011", "01", "자바프로그래밍", "김정민"),
                candidate("2026-1", "565011", "02", "자바프로그래밍", "김정민")));

        var result = matcher.match(
                OcrDocumentType.TRANSCRIPT,
                "2026-1",
                List.of(course("자바프로그래밍", null)));

        assertThat(result.courses()).singleElement().satisfies(course -> {
            assertThat(course.matchStatus()).isEqualTo(OcrCourseMatchStatus.COURSE_MATCHED);
            assertThat(course.credits()).isEqualByComparingTo("3.0");
            assertThat(course.category()).isEqualTo("전공선택");
            assertThat(course.semester()).isEqualTo("2026-1");
            assertThat(course.matchedSection()).isNull();
            assertThat(course.matchCandidates()).hasSize(2);
        });
    }

    @Test
    void prefillsPastCourseFromHistoricalOffering() {
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                historicalCandidate(
                        "history-2020-1-927313-01",
                        "2020-1",
                        "927313",
                        "01",
                        "컴퓨팅사고와문제해결",
                        "김지연",
                        "교필",
                        BigDecimal.valueOf(2),
                        session(DayOfWeek.WEDNESDAY, "11:30", "13:30", "정보 310"))));

        var result = matcher.match(
                OcrDocumentType.TRANSCRIPT,
                "2020년 1학기",
                List.of(course("컴퓨팅사고와문제해결", null)));

        assertThat(result.courses()).singleElement().satisfies(course -> {
            assertThat(course.matchStatus()).isEqualTo(OcrCourseMatchStatus.COURSE_MATCHED);
            assertThat(course.courseName()).isEqualTo("컴퓨팅사고와문제해결");
            assertThat(course.credits()).isEqualByComparingTo("2.0");
            assertThat(course.category()).isEqualTo("교양필수");
            assertThat(course.semester()).isEqualTo("2020-1");
            assertThat(course.matchCandidates()).singleElement().satisfies(candidate ->
                    assertThat(candidate.historicalOfferingId())
                            .isEqualTo("history-2020-1-927313-01"));
        });
    }

    @Test
    void prefillsStableHistoricalCourseWithoutInventingSemester() {
        when(semesterRepository.findAll(false)).thenReturn(List.of(semester("2026-1")));
        when(matchRepository.findHistoricalSemesterIds())
                .thenReturn(List.of("2021-1", "2020-1"));
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                historicalCandidate(
                        "history-2021-1-927345-01",
                        "2021-1",
                        "927345",
                        "01",
                        "현대사회의음악적다양성",
                        "차호성",
                        "교선",
                        BigDecimal.valueOf(2)),
                historicalCandidate(
                        "history-2020-1-927345-01",
                        "2020-1",
                        "927345",
                        "01",
                        "현대사회의음악적다양성",
                        "차호성",
                        "교선",
                        BigDecimal.valueOf(2))));

        var result = matcher.match(
                OcrDocumentType.TRANSCRIPT,
                null,
                List.of(course("현대사회의음악적다양성", null)));

        assertThat(result.resolvedSemester()).isNull();
        assertThat(result.courses()).singleElement().satisfies(course -> {
            assertThat(course.matchStatus()).isEqualTo(OcrCourseMatchStatus.COURSE_MATCHED);
            assertThat(course.credits()).isEqualByComparingTo("2.0");
            assertThat(course.category()).isEqualTo("교양선택");
            assertThat(course.semester()).isNull();
            assertThat(course.matchCandidates()).hasSize(2);
        });
    }

    @Test
    void usesAuthenticatedUsersAcademicUnitForCanonicalPrefill() {
        UUID userId = UUID.randomUUID();
        StudentProfile profile = new StudentProfile(userId, "20260001");
        profile.update(null, "D1", null, null, null, null);
        StudentProfileRepository profileRepository = mock(StudentProfileRepository.class);
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(matchRepository.findCandidates(anySet(), anySet(), eq("D1")))
                .thenReturn(List.of(
                        candidate("2026-1", "565011", "01", "자바프로그래밍", "김정민"),
                        candidate("2026-1", "565011", "02", "자바프로그래밍", "김정민")));
        CompletedCourseSectionMatcher userAwareMatcher =
                new CompletedCourseSectionMatcher(
                        matchRepository, semesterRepository, profileRepository);

        var result = userAwareMatcher.match(
                userId,
                OcrDocumentType.TRANSCRIPT,
                "2026-1",
                List.of(course("자바프로그래밍", null)));

        assertThat(result.courses()).singleElement().satisfies(course -> {
            assertThat(course.matchStatus()).isEqualTo(OcrCourseMatchStatus.COURSE_MATCHED);
            assertThat(course.credits()).isEqualByComparingTo("3.0");
            assertThat(course.category()).isEqualTo("전공선택");
        });
    }

    @Test
    void resolvesMissingHeaderFromMultiCourseSemesterConsensus() {
        when(semesterRepository.findAll(false)).thenReturn(List.of(
                semester("2026-2"), semester("2026-1")));
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                candidate("2026-2", "MATH2", "01", "공업수학II", "김교수",
                        session(DayOfWeek.MONDAY, "10:00", "11:30", "공다A210")),
                candidate("2026-2", "CIRCUIT2", "01", "회로이론II", "이교수",
                        session(DayOfWeek.TUESDAY, "11:30", "13:30", "공다A214")),
                candidate("2026-2", "WAR", "02", "세계전쟁사", "최교수",
                        session(DayOfWeek.WEDNESDAY, "13:30", "15:30", "인105")),
                candidate("2026-1", "WAR", "02", "세계전쟁사", "최교수",
                        session(DayOfWeek.WEDNESDAY, "13:30", "15:30", "인105"))));

        var result = matcher.match(OcrDocumentType.TIMETABLE, null, List.of(
                course("공업수학II", null,
                        meeting(DayOfWeek.MONDAY, "10:00", "11:30", "공다A210")),
                course("회로이론II", null,
                        meeting(DayOfWeek.TUESDAY, "11:30", "13:30", "공다A214")),
                course("세계전쟁사", null,
                        meeting(DayOfWeek.WEDNESDAY, "13:30", "15:30", "인105"))));

        assertThat(result.resolvedSemester()).isEqualTo("2026-2");
        assertThat(result.courses())
                .allMatch(course -> course.matchStatus() == OcrCourseMatchStatus.MATCHED)
                .allMatch(course -> course.matchedSection().semesterId().equals("2026-2"));
    }

    @Test
    void doesNotConfirmOneOfMultipleSectionsWhenVisibleRoomMatchesNone() {
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                candidate(
                        "2026-2",
                        "927381",
                        "05",
                        "LCT(LearningbyCommunication&Teamwork)",
                        "정소명",
                        session(DayOfWeek.THURSDAY, "09:30", "11:30", "인102")),
                candidate(
                        "2026-2",
                        "927381",
                        "06",
                        "LCT(LearningbyCommunication&Teamwork)",
                        "정소명",
                        session(DayOfWeek.THURSDAY, "11:30", "13:30", "인102"))));

        var result = matcher.match(OcrDocumentType.TIMETABLE, "2026-2", List.of(course(
                "LCT(Learning by Communication & Teamwork)",
                null,
                meeting(DayOfWeek.THURSDAY, "11:00", "13:00", "인110"))));

        assertThat(result.courses()).singleElement().satisfies(course -> {
            assertThat(course.matchStatus()).isEqualTo(OcrCourseMatchStatus.COURSE_MATCHED);
            assertThat(course.matchedSection()).isNull();
            assertThat(course.matchCandidates()).hasSize(2);
        });
    }

    @Test
    void soleCandidateWithContradictingVisibleEvidenceIsOnlyCourseMatched() {
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                candidate(
                        "2026-2",
                        "522026",
                        "01",
                        "회로이론II",
                        "김남준",
                        session(DayOfWeek.TUESDAY, "11:30", "13:30", "공다A214"))));

        var result = matcher.match(OcrDocumentType.TIMETABLE, "2026-2", List.of(course(
                "회로이론II",
                "다른교수",
                meeting(DayOfWeek.WEDNESDAY, "09:00", "11:00", "정보408"))));

        assertThat(result.courses()).singleElement().satisfies(course -> {
            assertThat(course.matchStatus()).isEqualTo(OcrCourseMatchStatus.COURSE_MATCHED);
            assertThat(course.matchedSection()).isNull();
            assertThat(course.matchCandidates()).singleElement().satisfies(candidate ->
                    assertThat(candidate.sectionCode()).isEqualTo("01"));
        });
    }

    @Test
    void sameProfessorAndWeekdayCannotHideSeveralHourTimeConflict() {
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                candidate(
                        "2026-2",
                        "CSE300",
                        "01",
                        "운영체제론",
                        "이시진",
                        session(DayOfWeek.MONDAY, "15:00", "16:30", "공다A411"))));

        var result = matcher.match(OcrDocumentType.TIMETABLE, "2026-2", List.of(course(
                "운영체제론",
                "이시진",
                meeting(DayOfWeek.MONDAY, "10:00", "11:30", "공다A411"))));

        assertThat(result.courses()).singleElement().satisfies(course -> {
            assertThat(course.matchStatus()).isEqualTo(OcrCourseMatchStatus.COURSE_MATCHED);
            assertThat(course.matchedSection()).isNull();
            assertThat(course.matchCandidates()).singleElement().satisfies(candidate -> {
                assertThat(candidate.matchScore()).isEqualByComparingTo("0.60");
                assertThat(candidate.matchedEvidence()).doesNotContain("MEETINGS_1_OF_1");
            });
        });
    }

    @Test
    void preservesPerCourseSemestersInMultiSemesterTranscript() {
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                candidate("2026-1", "CSE100", "01", "자료구조", "박교수",
                        session(DayOfWeek.MONDAY, "10:00", "11:30", "공101")),
                candidate("2026-2", "CSE100", "01", "자료구조", "박교수",
                        session(DayOfWeek.TUESDAY, "10:00", "11:30", "공102")),
                candidate("2026-2", "CSE200", "01", "알고리즘", "김교수",
                        session(DayOfWeek.WEDNESDAY, "13:30", "15:00", "공201"))));

        var result = matcher.match(OcrDocumentType.TRANSCRIPT, null, List.of(
                courseInSemester(
                        "자료구조",
                        "2026-1",
                        meeting(DayOfWeek.MONDAY, "10:00", "11:30", "공101")),
                courseInSemester(
                        "알고리즘",
                        "2026-2",
                        meeting(DayOfWeek.WEDNESDAY, "13:30", "15:00", "공201"))));

        assertThat(result.resolvedSemester()).isNull();
        assertThat(result.courses()).extracting(course ->
                        course.matchedSection().semesterId())
                .containsExactly("2026-1", "2026-2");
    }

    @Test
    void scheduleEvidenceBeatsCourseAvailabilityForTimetableSemester() {
        when(semesterRepository.findAll(false)).thenReturn(List.of(
                semester("2026-2"), semester("2026-1")));
        when(matchRepository.findCandidates(anySet(), anySet())).thenReturn(List.of(
                candidate("2026-1", "A", "01", "과목A", "교수A",
                        session(DayOfWeek.MONDAY, "10:00", "11:30", "R1")),
                candidate("2026-1", "B", "01", "과목B", "교수B",
                        session(DayOfWeek.TUESDAY, "10:00", "11:30", "R2")),
                candidate("2026-2", "A", "01", "과목A", "교수A",
                        session(DayOfWeek.FRIDAY, "15:00", "16:30", "X1")),
                candidate("2026-2", "B", "01", "과목B", "교수B",
                        session(DayOfWeek.FRIDAY, "15:00", "16:30", "X2")),
                candidate("2026-2", "C", "01", "과목C", "교수C",
                        session(DayOfWeek.FRIDAY, "15:00", "16:30", "X3")),
                candidate("2026-2", "D", "01", "과목D", "교수D",
                        session(DayOfWeek.FRIDAY, "15:00", "16:30", "X4")),
                candidate("2026-2", "E", "01", "과목E", "교수E",
                        session(DayOfWeek.FRIDAY, "15:00", "16:30", "X5"))));

        var result = matcher.match(OcrDocumentType.TIMETABLE, null, List.of(
                course("과목A", null,
                        meeting(DayOfWeek.MONDAY, "10:00", "11:30", "R1")),
                course("과목B", null,
                        meeting(DayOfWeek.TUESDAY, "10:00", "11:30", "R2")),
                course("과목C", null,
                        meeting(DayOfWeek.MONDAY, "10:00", "11:30", "R3")),
                course("과목D", null,
                        meeting(DayOfWeek.TUESDAY, "10:00", "11:30", "R4")),
                course("과목E", null,
                        meeting(DayOfWeek.WEDNESDAY, "10:00", "11:30", "R5"))));

        assertThat(result.resolvedSemester()).isEqualTo("2026-1");
        assertThat(result.courses().get(0).matchedSection().semesterId())
                .isEqualTo("2026-1");
        assertThat(result.courses().get(1).matchedSection().semesterId())
                .isEqualTo("2026-1");
        assertThat(result.courses().subList(2, 5))
                .allMatch(course -> course.matchStatus() == OcrCourseMatchStatus.UNMATCHED);
    }

    private static RecognizedCourseResponse course(
            String name,
            String professor,
            RecognizedCourseMeetingResponse... meetings) {
        return new RecognizedCourseResponse(
                name,
                null,
                CompletedCourseGradingBasis.LETTER,
                null,
                null,
                null,
                BigDecimal.valueOf(0.95),
                professor,
                List.of(meetings),
                OcrCourseMatchStatus.UNMATCHED,
                null,
                List.of());
    }

    private static RecognizedCourseResponse courseInSemester(
            String name,
            String semester,
            RecognizedCourseMeetingResponse... meetings) {
        RecognizedCourseResponse course = course(name, null, meetings);
        return new RecognizedCourseResponse(
                course.courseName(),
                course.credits(),
                course.gradingBasis(),
                course.category(),
                course.area(),
                semester,
                course.confidence(),
                course.professor(),
                course.meetings(),
                course.matchStatus(),
                course.matchedSection(),
                course.matchCandidates());
    }

    private static SectionCandidate candidate(
            String semester,
            String courseCode,
            String sectionCode,
            String courseName,
            String professor,
            CourseSessionResponse... sessions) {
        return new SectionCandidate(
                semester,
                courseCode,
                courseName,
                sectionCode,
                professor,
                "전공",
                "전선",
                BigDecimal.valueOf(3),
                null,
                null,
                List.of(sessions),
                null);
    }

    private static SectionCandidate historicalCandidate(
            String historicalOfferingId,
            String semester,
            String courseCode,
            String sectionCode,
            String courseName,
            String professor,
            String completionCategory,
            BigDecimal credits,
            CourseSessionResponse... sessions) {
        return new SectionCandidate(
                semester,
                courseCode,
                courseName,
                sectionCode,
                professor,
                completionCategory,
                completionCategory,
                credits,
                null,
                null,
                List.of(sessions),
                historicalOfferingId);
    }

    private static RecognizedCourseMeetingResponse meeting(
            DayOfWeek day, String start, String end, String room) {
        return new RecognizedCourseMeetingResponse(
                day, LocalTime.parse(start), LocalTime.parse(end), room);
    }

    private static CourseSessionResponse session(
            DayOfWeek day, String start, String end, String room) {
        return new CourseSessionResponse(
                day,
                LocalTime.parse(start),
                LocalTime.parse(end),
                null,
                room,
                null,
                List.of());
    }

    private static SemesterResponse semester(String id) {
        return new SemesterResponse(
                id, LocalDate.of(2026, 1, 1), "test", true, Instant.EPOCH);
    }
}
