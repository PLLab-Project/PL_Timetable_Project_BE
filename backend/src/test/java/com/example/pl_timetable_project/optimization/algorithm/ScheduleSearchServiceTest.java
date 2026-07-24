package com.example.pl_timetable_project.optimization.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScheduleSearchServiceTest {

    private final ScheduleSearchService scheduleSearchService = new ScheduleSearchService(new TopCombinationSelector());

    @Test
    void search_returnsCombinationsWithinCreditRangeContainingAllRequiredCourses() {
        List<CandidateCourse> required = List.of(
                course(1L, "필수1", 3, DayOfWeek.MONDAY, "09:00", "10:30"),
                course(2L, "필수2", 3, DayOfWeek.TUESDAY, "09:00", "10:30"));

        List<CandidateCourse> optional = List.of(
                course(10L, "선택1", 3, DayOfWeek.WEDNESDAY, "09:00", "10:30"),
                course(11L, "선택2", 3, DayOfWeek.THURSDAY, "09:00", "10:30"),
                course(12L, "선택3", 3, DayOfWeek.FRIDAY, "09:00", "10:30"),
                course(13L, "선택4", 2, DayOfWeek.MONDAY, "11:00", "12:00"),
                course(14L, "선택5", 2, DayOfWeek.TUESDAY, "11:00", "12:00"));

        OptimizationConstraints constraints = constraints(9, 14, 12, 5_000L);

        List<ScheduleCombination> results = scheduleSearchService.search(required, optional, constraints);

        assertThat(results).isNotEmpty();
        assertThat(results.size()).isLessThanOrEqualTo(3);
        for (ScheduleCombination combination : results) {
            assertThat(combination.totalCredit()).isBetween(9, 14);
            assertThat(combination.courses()).extracting(CandidateCourse::courseId).contains(1L, 2L);
            assertNoConflicts(combination.courses());
        }
    }

    @Test
    void search_neverSelectsConflictingCandidatesTogether() {
        List<CandidateCourse> required = List.of(
                course(1L, "필수1", 3, DayOfWeek.MONDAY, "09:00", "10:30"));

        // 20, 21은 같은 요일/시간대라 서로 충돌한다.
        List<CandidateCourse> optional = List.of(
                course(20L, "충돌A", 3, DayOfWeek.WEDNESDAY, "09:00", "10:30"),
                course(21L, "충돌B", 3, DayOfWeek.WEDNESDAY, "09:30", "11:00"),
                course(22L, "비충돌", 3, DayOfWeek.THURSDAY, "09:00", "10:30"));

        OptimizationConstraints constraints = constraints(3, 9, 9, 5_000L);

        List<ScheduleCombination> results = scheduleSearchService.search(required, optional, constraints);

        assertThat(results).isNotEmpty();
        for (ScheduleCombination combination : results) {
            Set<Long> ids = courseIds(combination);
            assertThat(ids.containsAll(Set.of(20L, 21L))).isFalse();
        }
    }

    @Test
    void search_returnsEmptyWhenCreditRangeIsUnreachable() {
        List<CandidateCourse> required = List.of(
                course(1L, "필수1", 3, DayOfWeek.MONDAY, "09:00", "10:30"));

        List<CandidateCourse> optional = List.of(
                course(10L, "선택1", 3, DayOfWeek.WEDNESDAY, "09:00", "10:30"));

        OptimizationConstraints constraints = constraints(100, 120, 110, 5_000L);

        List<ScheduleCombination> results = scheduleSearchService.search(required, optional, constraints);

        assertThat(results).isEmpty();
    }

    /**
     * 월요일 A그룹(A1~A3)끼리, 화요일 B그룹(B1~B3)끼리는 각각 같은 시간대라 서로 충돌하므로
     * 한쪽에서 하나씩만 선택 가능하다. 그룹 내 강의들은 학점/시간이 동일해 점수가 모두 같으므로,
     * CP-SAT이 매번 "최적해 찾기 → 그 해를 제외 → 재탐색"을 반복하면서
     * 서로 다른 (A_i, B_j) 조합 3개를 뽑아내는지 확인한다.
     */
    @Test
    void search_findsThreeDistinctTopCombinationsWhenManyTiedOptimaExist() {
        List<CandidateCourse> required = List.of();

        List<CandidateCourse> optional = List.of(
                course(100L, "A1", 3, DayOfWeek.MONDAY, "09:00", "10:30"),
                course(101L, "A2", 3, DayOfWeek.MONDAY, "09:00", "10:30"),
                course(102L, "A3", 3, DayOfWeek.MONDAY, "09:00", "10:30"),
                course(200L, "B1", 3, DayOfWeek.TUESDAY, "09:00", "10:30"),
                course(201L, "B2", 3, DayOfWeek.TUESDAY, "09:00", "10:30"),
                course(202L, "B3", 3, DayOfWeek.TUESDAY, "09:00", "10:30"));

        OptimizationConstraints constraints = constraints(6, 6, 6, 5_000L);

        List<ScheduleCombination> results = scheduleSearchService.search(required, optional, constraints);

        assertThat(results).hasSize(3);

        Set<Set<Long>> distinctCombinations = new HashSet<>();
        for (ScheduleCombination combination : results) {
            assertThat(combination.totalCredit()).isEqualTo(6);
            assertNoConflicts(combination.courses());
            distinctCombinations.add(courseIds(combination));
        }
        assertThat(distinctCombinations).hasSize(3);
    }

    private void assertNoConflicts(List<CandidateCourse> courses) {
        for (int i = 0; i < courses.size(); i++) {
            for (int j = i + 1; j < courses.size(); j++) {
                assertThat(courses.get(i).conflictsWith(courses.get(j))).isFalse();
            }
        }
    }

    private Set<Long> courseIds(ScheduleCombination combination) {
        return combination.courses().stream().map(CandidateCourse::courseId).collect(Collectors.toSet());
    }

    private CandidateCourse course(Long courseId, String name, int credit, DayOfWeek day, String start, String end) {
        return new CandidateCourse(courseId, name, "교수" + courseId, credit,
                List.of(new CourseTimeSlot(day, LocalTime.parse(start), LocalTime.parse(end))));
    }

    private OptimizationConstraints constraints(int minCredit, int maxCredit, int targetCredit, long searchTimeLimitMillis) {
        return new OptimizationConstraints(minCredit, maxCredit, targetCredit, Set.of(), Set.of(),
                LocalTime.of(8, 0), LocalTime.of(18, 0), LocalTime.of(12, 0), LocalTime.of(13, 0),
                480, searchTimeLimitMillis);
    }
}
