package com.example.pl_timetable_project.optimization.algorithm;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 두 시간표 조합이 강의 구성 면에서 얼마나 겹치는지(자카드 유사도) 계산한다.
 * 정렬/상위 N개 선정 로직은 {@link ScheduleSearchService}가 CP-SAT 반복 탐색 과정에서
 * "최적해 찾기 → 기존 채택 결과와 겹치면 제외 → 재탐색" 방식으로 직접 수행하므로 더 이상 필요 없다.
 */
@Component
public class TopCombinationSelector {

    public double overlapRatio(List<CandidateCourse> a, List<CandidateCourse> b) {
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

    private Set<Long> courseIds(List<CandidateCourse> courses) {
        return courses.stream().map(CandidateCourse::courseId).collect(Collectors.toSet());
    }
}
