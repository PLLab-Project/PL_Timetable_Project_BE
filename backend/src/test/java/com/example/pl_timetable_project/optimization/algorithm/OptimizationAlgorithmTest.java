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
                1_000);

        List<CandidateCourse> filtered = new CandidateCourseFilter().filter(
                List.of(morning, afternoon, blocked, outside), constraints);

        assertThat(filtered)
                .extracting(course -> course.section().getCourseCode())
                .containsExactly("CSE100", "CSE200");
    }

    private CandidateCourse candidate(
            String courseCode,
            String sectionCode,
            DayOfWeek day,
            int startHour,
            int endHour) {
        return new CandidateCourse(
                new SectionReference("2026-1", courseCode, sectionCode),
                "자료구조",
                "홍길동",
                300,
                false,
                List.of(new CourseTimeSlot(
                        day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0))));
    }
}
