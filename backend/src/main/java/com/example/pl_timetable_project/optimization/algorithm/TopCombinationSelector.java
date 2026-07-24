package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.academic.section.SectionReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TopCombinationSelector {

    private static final int TOP_N = 3;
    private static final double OVERLAP_THRESHOLD = 0.7;

    public List<ScoredCombination> selectTop(List<ScoredCombination> combinations) {
        List<ScoredCombination> sorted = combinations.stream()
                .sorted(Comparator.comparingDouble(ScoredCombination::score).reversed())
                .toList();
        List<ScoredCombination> selected = new ArrayList<>();
        List<ScoredCombination> deferred = new ArrayList<>();

        for (ScoredCombination candidate : sorted) {
            if (selected.size() >= TOP_N) {
                break;
            }
            if (selected.stream().anyMatch(
                    chosen -> overlapRatio(chosen, candidate) >= OVERLAP_THRESHOLD)) {
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

    /** CP-SAT 반복 탐색에서 두 강의 조합의 중복 비율을 계산합니다. */
    public double overlapRatio(List<CandidateCourse> left, List<CandidateCourse> right) {
        Set<SectionReference> leftSections = sections(left);
        Set<SectionReference> rightSections = sections(right);
        Set<SectionReference> intersection = new HashSet<>(leftSections);
        intersection.retainAll(rightSections);
        Set<SectionReference> union = new HashSet<>(leftSections);
        union.addAll(rightSections);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private double overlapRatio(ScoredCombination left, ScoredCombination right) {
        Set<SectionReference> leftSections = sections(left);
        Set<SectionReference> rightSections = sections(right);
        Set<SectionReference> intersection = new HashSet<>(leftSections);
        intersection.retainAll(rightSections);
        Set<SectionReference> union = new HashSet<>(leftSections);
        union.addAll(rightSections);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<SectionReference> sections(ScoredCombination combination) {
        return sections(combination.combination().courses());
    }

    private Set<SectionReference> sections(List<CandidateCourse> courses) {
        return courses.stream()
                .map(CandidateCourse::section)
                .collect(Collectors.toSet());
    }
}
