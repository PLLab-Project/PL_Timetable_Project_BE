package com.example.pl_timetable_project.optimization.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 점수 순으로 정렬한 뒤, 이미 선정된 조합과 강의 구성이 70% 이상 겹치는 조합은
 * 뒤로 미뤄 상위 3개 안에서 서로 다른 조합이 나오도록 한다.
 * 다양성 조건을 만족하는 후보가 3개에 못 미치면 미뤄뒀던 조합으로 남은 자리를 채운다.
 */
@Component
public class TopCombinationSelector {

    private static final int TOP_N = 3;
    private static final double OVERLAP_THRESHOLD = 0.7;

    public List<ScoredCombination> selectTop(List<ScoredCombination> scoredCombinations) {
        List<ScoredCombination> sorted = scoredCombinations.stream()
                .sorted(Comparator.comparingDouble(ScoredCombination::score).reversed())
                .toList();

        List<ScoredCombination> selected = new ArrayList<>();
        List<ScoredCombination> deferred = new ArrayList<>();

        for (ScoredCombination candidate : sorted) {
            if (selected.size() >= TOP_N) {
                break;
            }
            boolean tooSimilarToSelected = selected.stream()
                    .anyMatch(chosen -> overlapRatio(chosen, candidate) >= OVERLAP_THRESHOLD);
            if (tooSimilarToSelected) {
                deferred.add(candidate);
            } else {
                selected.add(candidate);
            }
        }

        for (ScoredCombination candidate : deferred) {
            if (selected.size() >= TOP_N) {
                break;
            }
            selected.add(candidate);
        }

        return selected;
    }

    private double overlapRatio(ScoredCombination a, ScoredCombination b) {
        Set<Long> idsA = courseIds(a);
        Set<Long> idsB = courseIds(b);

        Set<Long> intersection = new HashSet<>(idsA);
        intersection.retainAll(idsB);

        Set<Long> union = new HashSet<>(idsA);
        union.addAll(idsB);

        if (union.isEmpty()) {
            return 0.0;
        }
        return (double) intersection.size() / union.size();
    }

    private Set<Long> courseIds(ScoredCombination combination) {
        return combination.combination().courses().stream()
                .map(CandidateCourse::courseId)
                .collect(Collectors.toSet());
    }
}
