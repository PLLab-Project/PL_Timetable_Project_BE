package com.example.pl_timetable_project.optimization.algorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.pl_timetable_project.academic.section.SectionReference;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ScheduleScorer의 본인 학과 가중치 계산이 ScheduleSearchService.SAME_MAJOR_BONUS와
 * 같은 크기로, 같은 조건(선택 과목만 대상)으로 동작하는지 검증한다.
 */
class ScheduleScorerTest {

    private static final double SAME_MAJOR_BONUS = 5.0;

    private final ScheduleScorer scorer = new ScheduleScorer();

    @Test
    void addsSameMajorBonusOnlyForMatchingOptionalCourse() {
        OptimizationConstraints constraints = constraints(List.of("D1"));

        ScoredCombination ownMajor = scorer.score(
                combination(course("CSE100", List.of("D1"), false)), constraints);
        ScoredCombination otherMajor = scorer.score(
                combination(course("PHY100", List.of("D2"), false)), constraints);
        ScoredCombination common = scorer.score(
                combination(course("GEN100", List.of(), false)), constraints);

        assertThat(ownMajor.score() - otherMajor.score())
                .isCloseTo(SAME_MAJOR_BONUS, within(1e-9));
        assertThat(ownMajor.score() - common.score())
                .isCloseTo(SAME_MAJOR_BONUS, within(1e-9));
        assertThat(otherMajor.score()).isCloseTo(common.score(), within(1e-9));
    }

    @Test
    void doesNotAddBonusForRequiredCourseEvenIfItMatchesTheUsersMajor() {
        OptimizationConstraints constraints = constraints(List.of("D1"));

        ScoredCombination requiredOwnMajor = scorer.score(
                combination(course("CSE100", List.of("D1"), true)), constraints);
        ScoredCombination requiredOtherMajor = scorer.score(
                combination(course("PHY100", List.of("D2"), true)), constraints);

        assertThat(requiredOwnMajor.score())
                .isCloseTo(requiredOtherMajor.score(), within(1e-9));
    }

    @Test
    void appliesNoBonusWhenUserHasNoAcademicUnitCode() {
        OptimizationConstraints constraints = constraints(List.of());

        ScoredCombination ownMajor = scorer.score(
                combination(course("CSE100", List.of("D1"), false)), constraints);
        ScoredCombination otherMajor = scorer.score(
                combination(course("PHY100", List.of("D2"), false)), constraints);

        assertThat(ownMajor.score()).isCloseTo(otherMajor.score(), within(1e-9));
    }

    private CandidateCourse course(
            String courseCode, List<String> restrictedAcademicUnitCodes, boolean required) {
        return new CandidateCourse(
                new SectionReference("2026-1", courseCode, "01"),
                "테스트과목",
                "담당교수",
                300,
                required,
                List.of(new CourseTimeSlot(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))),
                restrictedAcademicUnitCodes);
    }

    private ScheduleCombination combination(CandidateCourse course) {
        return new ScheduleCombination(List.of(course), course.creditUnits());
    }

    private OptimizationConstraints constraints(List<String> userAcademicUnitCodes) {
        return new OptimizationConstraints(
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
                1_000,
                userAcademicUnitCodes);
    }
}
