package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.exception.OptimizationTimeoutException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 필수 분반을 고정한 뒤 선택 후보를 포함/제외하며 가능한 조합을 탐색한다.
 */
@Component
public class ScheduleSearchService {

    private static final int MAX_RESULTS = 200;

    public List<ScheduleCombination> search(
            List<CandidateCourse> requiredCourses,
            List<CandidateCourse> optionalCandidates,
            OptimizationConstraints constraints) {
        List<ScheduleCombination> results = new ArrayList<>();
        int requiredCredits = requiredCourses.stream()
                .mapToInt(CandidateCourse::creditUnits)
                .sum();

        if (requiredCredits > constraints.maxCreditUnits()) {
            return results;
        }

        int[] suffixMaxCredits = buildSuffixMaxCredits(optionalCandidates);
        long deadlineNanos =
                System.nanoTime() + constraints.searchTimeLimitMillis() * 1_000_000L;
        List<CandidateCourse> selected = new ArrayList<>(requiredCourses);
        backtrack(
                0,
                optionalCandidates,
                selected,
                requiredCredits,
                suffixMaxCredits,
                constraints,
                deadlineNanos,
                results);
        return results;
    }

    private void backtrack(
            int index,
            List<CandidateCourse> candidates,
            List<CandidateCourse> selected,
            int currentCredits,
            int[] suffixMaxCredits,
            OptimizationConstraints constraints,
            long deadlineNanos,
            List<ScheduleCombination> results) {
        if (results.size() >= MAX_RESULTS) {
            return;
        }
        if (System.nanoTime() > deadlineNanos) {
            throw new OptimizationTimeoutException(
                    "시간표 탐색이 제한 시간("
                            + constraints.searchTimeLimitMillis() + "ms)을 초과했습니다.");
        }
        if (index == candidates.size()) {
            if (currentCredits >= constraints.minCreditUnits()
                    && currentCredits <= constraints.maxCreditUnits()) {
                results.add(new ScheduleCombination(List.copyOf(selected), currentCredits));
            }
            return;
        }
        if (currentCredits + suffixMaxCredits[index] < constraints.minCreditUnits()) {
            return;
        }

        CandidateCourse candidate = candidates.get(index);
        boolean conflict = selected.stream().anyMatch(candidate::conflictsWith);
        boolean withinCreditLimit =
                currentCredits + candidate.creditUnits() <= constraints.maxCreditUnits();

        if (!conflict && withinCreditLimit) {
            selected.add(candidate);
            backtrack(
                    index + 1,
                    candidates,
                    selected,
                    currentCredits + candidate.creditUnits(),
                    suffixMaxCredits,
                    constraints,
                    deadlineNanos,
                    results);
            selected.remove(selected.size() - 1);
        }

        backtrack(
                index + 1,
                candidates,
                selected,
                currentCredits,
                suffixMaxCredits,
                constraints,
                deadlineNanos,
                results);
    }

    private int[] buildSuffixMaxCredits(List<CandidateCourse> candidates) {
        int[] suffix = new int[candidates.size() + 1];
        for (int i = candidates.size() - 1; i >= 0; i--) {
            suffix[i] = suffix[i + 1] + candidates.get(i).creditUnits();
        }
        return suffix;
    }
}
