package com.example.pl_timetable_project.optimization.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pl_timetable_project.academic.section.SectionReference;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptimizationAlgorithmTest {

    private final ScheduleSearchService searchService =
            new ScheduleSearchService(new TopCombinationSelector());

    @Test
    void neverSelectsTwoAlternativeSectionsOfTheSameCourse() {
        CandidateCourse sectionOne = candidate(
                "CSE100", "01", DayOfWeek.MONDAY, 9, 11);
        CandidateCourse sectionTwo = candidate(
                "CSE100", "02", DayOfWeek.TUESDAY, 9, 11);
        OptimizationConstraints constraints = new OptimizationConstraints(
                0,
                600,
                300,
                Set.of(),
                Set.of(),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                480,
                1_000);

        List<ScheduleCombination> combinations = searchService.search(
                List.of(), List.of(sectionOne, sectionTwo), constraints);

        assertThat(combinations).isNotEmpty();
        assertThat(combinations)
                .allSatisfy(combination -> assertThat(combination.courses())
                        .extracting(course -> course.section().getCourseCode())
                        .doesNotHaveDuplicates());
    }

    @Test
    void filtersUsingMultipleAvailableRangesAndBlockedTimes() {
        CandidateCourse morning = candidate(
                "CSE100", "01", DayOfWeek.MONDAY, 9, 11);
        CandidateCourse afternoon = candidate(
                "CSE200", "01", DayOfWeek.TUESDAY, 14, 16);
        CandidateCourse blocked = candidate(
                "CSE300", "01", DayOfWeek.WEDNESDAY, 14, 16);
        CandidateCourse outside = candidate(
                "CSE400", "01", DayOfWeek.THURSDAY, 12, 14);
        OptimizationConstraints constraints = new OptimizationConstraints(
                0,
                1_200,
                600,
                Set.of(),
                Set.of(),
                List.of(
                        new OptimizationTimeRange(
                                LocalTime.of(9, 0), LocalTime.of(11, 0)),
                        new OptimizationTimeRange(
                                LocalTime.of(14, 0), LocalTime.of(18, 0))),
                List.of(new CourseTimeSlot(
                        DayOfWeek.WEDNESDAY,
                        LocalTime.of(13, 0),
                        LocalTime.of(17, 0))),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                480,
                1_000,
                List.of(),
                List.of(),
                Set.of());

        List<CandidateCourse> filtered = new CandidateCourseFilter().filter(
                List.of(morning, afternoon, blocked, outside), constraints);

        assertThat(filtered)
                .extracting(course -> course.section().getCourseCode())
                .containsExactly("CSE100", "CSE200");
    }

    @Test
    void prefersOwnMajorSectionOverOtherMajorWhenScheduleQualityTies() {
        CandidateCourse ownMajor = candidate(
                "CSE100", "01", DayOfWeek.MONDAY, 9, 11, List.of("D1"));
        CandidateCourse otherMajor = candidate(
                "PHY100", "01", DayOfWeek.TUESDAY, 9, 11, List.of("D2"));
        // 두 후보는 요일만 다를 뿐 학점·시간대·점심 확보 여부가 완전히 동일하므로,
        // 목표학점(3학점)에 하나만 필요한 상황에서 순수하게 SAME_MAJOR_BONUS만으로
        // 우열이 갈린다.
        OptimizationConstraints constraints = new OptimizationConstraints(
                0,
                600,
                300,
                Set.of(),
                Set.of(),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                480,
                5_000,
                List.of("D1"));

        List<ScheduleCombination> results = searchService.search(
                List.of(), List.of(ownMajor, otherMajor), constraints);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).courses())
                .extracting(course -> course.section().getCourseCode())
                .contains("CSE100")
                .doesNotContain("PHY100");
        // 타 전공 강의가 하드 배제되지는 않았음을 확인한다 — 상위 결과 어딘가에는 등장한다.
        assertThat(results)
                .flatExtracting(ScheduleCombination::courses)
                .extracting(course -> course.section().getCourseCode())
                .contains("PHY100");
    }

    @Test
    void stillIncludesOtherMajorSectionWhenNeededToMeetCreditRange() {
        CandidateCourse otherMajor = candidate(
                "PHY100", "01", DayOfWeek.MONDAY, 9, 11, List.of("D2"));
        // 사용자는 D1 소속이고 유일한 후보는 D2 전용 강의뿐이다. 학점 범위가
        // [3,3]으로 고정돼 있어 이 강의를 포함하지 않으면 애초에 실행 가능한
        // 조합이 없다 — 하드 배제였다면 이 테스트는 결과 없음/예외가 나야 한다.
        OptimizationConstraints constraints = new OptimizationConstraints(
                300,
                300,
                300,
                Set.of(),
                Set.of(),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                480,
                5_000,
                List.of("D1"));

        List<ScheduleCombination> results = searchService.search(
                List.of(), List.of(otherMajor), constraints);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).courses())
                .extracting(course -> course.section().getCourseCode())
                .containsExactly("PHY100");
    }

    private CandidateCourse candidate(
            String courseCode,
            String sectionCode,
            DayOfWeek day,
            int startHour,
            int endHour) {
        return candidate(courseCode, sectionCode, day, startHour, endHour, List.of());
    }

    private CandidateCourse candidate(
            String courseCode,
            String sectionCode,
            DayOfWeek day,
            int startHour,
            int endHour,
            List<String> restrictedAcademicUnitCodes) {
        return new CandidateCourse(
                new SectionReference("2026-1", courseCode, sectionCode),
                "자료구조",
                "홍길동",
                300,
                false,
                List.of(new CourseTimeSlot(
                        day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0))),
                restrictedAcademicUnitCodes,
                null,
                List.of());
    }
}
