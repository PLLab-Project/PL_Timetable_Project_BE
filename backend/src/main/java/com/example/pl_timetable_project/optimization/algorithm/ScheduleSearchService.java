package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.exception.OptimizationTimeoutException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 필수 강의를 고정한 상태에서 선택 가능한 후보 강의들을 포함/제외하며
 * 조건(학점 범위)을 만족하는 시간표 조합을 백트래킹으로 탐색한다.
 */
@Component
public class ScheduleSearchService {

    private static final int MAX_RESULTS = 200;

    public List<ScheduleCombination> search(List<CandidateCourse> requiredCourses,
                                             List<CandidateCourse> optionalCandidates,
                                             OptimizationConstraints constraints) {
        List<ScheduleCombination> results = new ArrayList<>();
        int requiredCredit = requiredCourses.stream().mapToInt(CandidateCourse::credit).sum();

        if (requiredCredit > constraints.maxCredit()) {
            return results;
        }

        int[] suffixMaxCredit = buildSuffixMaxCredit(optionalCandidates);
        long deadlineNanos = System.nanoTime() + constraints.searchTimeLimitMillis() * 1_000_000L;

        List<CandidateCourse> selected = new ArrayList<>(requiredCourses);
        backtrack(0, optionalCandidates, selected, requiredCredit, suffixMaxCredit, constraints, deadlineNanos, results);

        return results;
    }

    private void backtrack(int index, List<CandidateCourse> optionalCandidates, List<CandidateCourse> selected,
                            int currentCredit, int[] suffixMaxCredit, OptimizationConstraints constraints,
                            long deadlineNanos, List<ScheduleCombination> results) {
        if (results.size() >= MAX_RESULTS) {
            return;
        }
        if (System.nanoTime() > deadlineNanos) {
            throw new OptimizationTimeoutException(
                    "시간표 편성 탐색이 제한 시간(" + constraints.searchTimeLimitMillis() + "ms)을 초과했습니다.");
        }

        if (index == optionalCandidates.size()) {
            if (currentCredit >= constraints.minCredit() && currentCredit <= constraints.maxCredit()) {
                results.add(new ScheduleCombination(List.copyOf(selected), currentCredit));
            }
            return;
        }

        // 가지치기: 남은 후보를 전부 포함해도 최소학점에 못 미치면 이 분기는 더 볼 필요가 없다.
        if (currentCredit + suffixMaxCredit[index] < constraints.minCredit()) {
            return;
        }

        CandidateCourse candidate = optionalCandidates.get(index);
        boolean conflict = hasConflict(selected, candidate);
        boolean withinCreditLimit = currentCredit + candidate.credit() <= constraints.maxCredit();

        if (!conflict && withinCreditLimit) {
            selected.add(candidate);
            backtrack(index + 1, optionalCandidates, selected, currentCredit + candidate.credit(),
                    suffixMaxCredit, constraints, deadlineNanos, results);
            selected.remove(selected.size() - 1);
        }

        if (results.size() >= MAX_RESULTS) {
            return;
        }

        backtrack(index + 1, optionalCandidates, selected, currentCredit, suffixMaxCredit, constraints, deadlineNanos, results);
    }

    private boolean hasConflict(List<CandidateCourse> selected, CandidateCourse candidate) {
        for (CandidateCourse course : selected) {
            if (course.conflictsWith(candidate)) {
                return true;
            }
        }
        return false;
    }

    private int[] buildSuffixMaxCredit(List<CandidateCourse> candidates) {
        int n = candidates.size();
        int[] suffix = new int[n + 1];
        for (int i = n - 1; i >= 0; i--) {
            suffix[i] = suffix[i + 1] + candidates.get(i).credit();
        }
        return suffix;
    }
}
