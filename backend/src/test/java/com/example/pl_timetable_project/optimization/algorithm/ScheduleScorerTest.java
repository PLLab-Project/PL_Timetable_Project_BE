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
 * ScheduleScorer의 본인 학과 가중치·교양 영역+선수과목 조건부 가중치 계산이
 * ScheduleSearchService의 상수와 같은 크기로, 같은 조건으로 동작하는지 검증한다.
 */
class ScheduleScorerTest {

    private static final double SAME_MAJOR_BONUS = 5.0;
    private static final double SEQUENCE_BONUS = 5.0;
    private static final double GRADUATION_PRIORITY_BONUS = 8.0;
    private static final String SCIENCE_AREA = "제3영역:과학과기술";
    private static final String ARTS_AREA = "제4영역:예술과문화";

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

    @Test
    void addsSameMajorBonusForEitherOfTheUsersMultipleAcademicUnits() {
        // 복수전공생(주전공 D1, 복수전공 D2)은 두 학과 강의 모두 본인 전공 취급되어
        // 같은 크기의 가중치를 받아야 하고, 어느 쪽에도 속하지 않는 학과(D3) 강의보다
        // 둘 다 SAME_MAJOR_BONUS만큼 높아야 한다.
        OptimizationConstraints constraints = constraints(List.of("D1", "D2"));

        ScoredCombination primaryMajor = scorer.score(
                combination(course("CSE100", List.of("D1"), false)), constraints);
        ScoredCombination doubleMajor = scorer.score(
                combination(course("BUS100", List.of("D2"), false)), constraints);
        ScoredCombination otherUnit = scorer.score(
                combination(course("PSY100", List.of("D3"), false)), constraints);

        assertThat(primaryMajor.score()).isCloseTo(doubleMajor.score(), within(1e-9));
        assertThat(primaryMajor.score() - otherUnit.score())
                .isCloseTo(SAME_MAJOR_BONUS, within(1e-9));
    }

    @Test
    void addsGraduationPriorityBonusOnlyForMatchingOptionalCourse() {
        OptimizationConstraints constraints =
                graduationPriorityConstraints(Set.of("CSE202"));

        ScoredCombination priorityCourse = scorer.score(
                combination(course("CSE202", List.of(), false)), constraints);
        ScoredCombination otherCourse = scorer.score(
                combination(course("CSE300", List.of(), false)), constraints);

        assertThat(priorityCourse.score() - otherCourse.score())
                .isCloseTo(GRADUATION_PRIORITY_BONUS, within(1e-9));
    }

    @Test
    void doesNotAddGraduationPriorityBonusForRequiredCourse() {
        // 이미 필수로 고정된 강의는 선택의 여지가 없으므로 가중치를 받아도 결과에
        // 영향이 없다 — SAME_MAJOR_BONUS/SEQUENCE_BONUS와 동일하게 required는 제외한다.
        OptimizationConstraints constraints =
                graduationPriorityConstraints(Set.of("CSE202"));

        ScoredCombination requiredPriorityCourse = scorer.score(
                combination(course("CSE202", List.of(), true)), constraints);
        ScoredCombination requiredOtherCourse = scorer.score(
                combination(course("CSE300", List.of(), true)), constraints);

        assertThat(requiredPriorityCourse.score())
                .isCloseTo(requiredOtherCourse.score(), within(1e-9));
    }

    @Test
    void doesNotDoubleCountGraduationPriorityBonusForSameCourseAppearingTwice() {
        // 같은 과목의 서로 다른 분반이 한 조합에 동시에 들어가는 일은 실제로는
        // 시간 충돌 하드 제약(section.sameCourse)이 막아주지만, 스코어러 자체는
        // 방어적으로 "과목 코드 기준 distinct"로 계산한다. 같은 조합을 우선배치
        // 적용/미적용으로 두 번 채점해 다른 항목(등교일수·공강 등)은 그대로 두고
        // 가중치 기여분만 분리해서 본다 — 이중 반영이면 차이가 BONUS*2가 된다.
        CandidateCourse sectionOne = new CandidateCourse(
                new SectionReference("2026-1", "CSE202", "01"),
                "테스트교양과목", "담당교수", 300, false,
                List.of(new CourseTimeSlot(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))),
                List.of(), null, List.of(), null);
        CandidateCourse sectionTwo = new CandidateCourse(
                new SectionReference("2026-1", "CSE202", "02"),
                "테스트교양과목", "담당교수", 300, false,
                List.of(new CourseTimeSlot(
                        DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))),
                List.of(), null, List.of(), null);
        ScheduleCombination combination =
                new ScheduleCombination(List.of(sectionOne, sectionTwo), 600);

        ScoredCombination withPriority = scorer.score(
                combination, graduationPriorityConstraints(Set.of("CSE202")));
        ScoredCombination withoutPriority = scorer.score(
                combination, graduationPriorityConstraints(Set.of()));

        assertThat(withPriority.score() - withoutPriority.score())
                .isCloseTo(GRADUATION_PRIORITY_BONUS, within(1e-9));
    }

    @Test
    void addsSequenceBonusWhenAreaSelectedAndPrerequisiteCompleted() {
        OptimizationConstraints constraints = sequenceConstraints(
                List.of(SCIENCE_AREA), Set.of("005111"));

        ScoredCombination withPrerequisiteMet = scorer.score(
                combination(liberalCourse("005112", SCIENCE_AREA, List.of("005111"))),
                constraints);
        ScoredCombination withoutPrerequisiteHint = scorer.score(
                combination(liberalCourse("924011", SCIENCE_AREA, List.of())),
                constraints);

        assertThat(withPrerequisiteMet.score() - withoutPrerequisiteHint.score())
                .isCloseTo(SEQUENCE_BONUS, within(1e-9));
    }

    @Test
    void appliesNoSequenceBonusWhenAreaNotSelected() {
        // 선수과목(005111)은 이수했지만 사용자가 어떤 교양 영역도 선택하지 않았다.
        OptimizationConstraints constraints = sequenceConstraints(
                List.of(), Set.of("005111"));

        ScoredCombination withPrerequisiteMet = scorer.score(
                combination(liberalCourse("005112", SCIENCE_AREA, List.of("005111"))),
                constraints);
        ScoredCombination withoutPrerequisiteHint = scorer.score(
                combination(liberalCourse("924011", SCIENCE_AREA, List.of())),
                constraints);

        assertThat(withPrerequisiteMet.score())
                .isCloseTo(withoutPrerequisiteHint.score(), within(1e-9));
    }

    @Test
    void appliesNoSequenceBonusWhenPrerequisiteNotCompleted() {
        // 제3영역을 선택했지만 선수과목(005111)을 아직 이수하지 않았다.
        OptimizationConstraints constraints = sequenceConstraints(
                List.of(SCIENCE_AREA), Set.of());

        ScoredCombination withPrerequisiteHint = scorer.score(
                combination(liberalCourse("005112", SCIENCE_AREA, List.of("005111"))),
                constraints);
        ScoredCombination withoutPrerequisiteHint = scorer.score(
                combination(liberalCourse("924011", SCIENCE_AREA, List.of())),
                constraints);

        assertThat(withPrerequisiteHint.score())
                .isCloseTo(withoutPrerequisiteHint.score(), within(1e-9));
    }

    @Test
    void appliesNoSequenceBonusWhenCourseIsInADifferentArea() {
        // 선수과목은 이수했지만 후보 과목이 선택한 영역(제3영역)이 아니라
        // 다른 영역(제4영역)이다.
        OptimizationConstraints constraints = sequenceConstraints(
                List.of(SCIENCE_AREA), Set.of("005111"));

        ScoredCombination differentArea = scorer.score(
                combination(liberalCourse("004317", ARTS_AREA, List.of("005111"))),
                constraints);
        ScoredCombination noHint = scorer.score(
                combination(liberalCourse("924011", SCIENCE_AREA, List.of())),
                constraints);

        assertThat(differentArea.score()).isCloseTo(noHint.score(), within(1e-9));
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
                restrictedAcademicUnitCodes,
                null,
                List.of(),
                null);
    }

    private CandidateCourse liberalCourse(
            String courseCode, String liberalAreaCode, List<String> prerequisiteCourseCodes) {
        return new CandidateCourse(
                new SectionReference("2026-1", courseCode, "01"),
                "테스트교양과목",
                "담당교수",
                300,
                false,
                List.of(new CourseTimeSlot(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))),
                List.of(),
                liberalAreaCode,
                prerequisiteCourseCodes,
                null);
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

    private OptimizationConstraints sequenceConstraints(
            List<String> selectedLiberalAreas, Set<String> completedCourseCodes) {
        return graduationPriorityConstraints(selectedLiberalAreas, completedCourseCodes, Set.of());
    }

    private OptimizationConstraints graduationPriorityConstraints(
            Set<String> graduationPriorityCourseCodes) {
        return graduationPriorityConstraints(List.of(), Set.of(), graduationPriorityCourseCodes);
    }

    private OptimizationConstraints graduationPriorityConstraints(
            List<String> selectedLiberalAreas,
            Set<String> completedCourseCodes,
            Set<String> graduationPriorityCourseCodes) {
        return new OptimizationConstraints(
                300,
                300,
                300,
                Set.of(),
                Set.of(),
                List.of(new OptimizationTimeRange(LocalTime.of(8, 0), LocalTime.of(20, 0))),
                List.of(),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                480,
                1_000,
                List.of(),
                selectedLiberalAreas,
                completedCourseCodes,
                graduationPriorityCourseCodes);
    }
}
