package com.example.pl_timetable_project.optimization.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pl_timetable_project.academic.section.SectionReference;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptimizationAlgorithmTest {

    private final ScheduleSearchService searchService = new ScheduleSearchService();

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
