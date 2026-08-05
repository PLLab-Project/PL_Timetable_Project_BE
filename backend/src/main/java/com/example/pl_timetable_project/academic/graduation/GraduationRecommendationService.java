package com.example.pl_timetable_project.academic.graduation;

import com.example.pl_timetable_project.academic.common.LiberalAreaCode;
import com.example.pl_timetable_project.academic.graduation.GraduationProgressCalculator.CreditCategory;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.OfferedCourse;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.RuleProfile;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.AreaGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.CreditGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Recommendation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourseGap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 현재 학기 개설과목 중 졸업요건 부족분을 채울 수 있는 과목을 선별합니다. */
@Component
public class GraduationRecommendationService {

    private static final int MAX_RECOMMENDATIONS = 20;

    private final GraduationQueryRepository repository;
    private final GraduationProgressCalculator progressCalculator;

    public GraduationRecommendationService(
            GraduationQueryRepository repository,
            GraduationProgressCalculator progressCalculator) {
        this.repository = repository;
        this.progressCalculator = progressCalculator;
    }

    List<Recommendation> recommend(
            UUID userId,
            String semesterId,
            RuleProfile profile,
            List<CreditGap> creditGaps,
            List<AreaGap> areaGaps,
            List<RequiredCourseGap> requiredCourseGaps) {
        Set<String> gapCodes = creditGaps.stream()
                .map(CreditGap::code)
                .collect(Collectors.toSet());
        Set<String> missingAreas = areaGaps.stream()
                .map(AreaGap::area)
                .collect(Collectors.toSet());
        List<Recommendation> result = new ArrayList<>();
        for (OfferedCourse course : repository.findOfferedCourses(
                userId, semesterId, profile.academicUnitCode())) {
            LinkedHashSet<String> fills = matchingRequirements(
                    course, requiredCourseGaps);
            CreditCategory category = progressCalculator.classify(course.category());
            boolean eligibleForCategory = category.isLiberal()
                    || profile.academicUnitCode() == null
                    || course.academicUnitMatch();
            addCreditGaps(fills, gapCodes, category, eligibleForCategory);
            addAreaGap(fills, missingAreas, course.category());
            if (fills.isEmpty()) {
                continue;
            }
            result.add(new Recommendation(
                    course.semesterId(),
                    course.courseCode(),
                    course.courseName(),
                    course.category(),
                    course.credits(),
                    course.sectionCount(),
                    List.copyOf(fills)));
        }
        return result.stream()
                .sorted(Comparator
                        .comparingInt((Recommendation value) ->
                                value.fills().size())
                        .reversed()
                        .thenComparing(Recommendation::courseName)
                        .thenComparing(Recommendation::courseCode))
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    private LinkedHashSet<String> matchingRequirements(
            OfferedCourse course,
            List<RequiredCourseGap> requiredCourseGaps) {
        LinkedHashSet<String> fills = new LinkedHashSet<>();
        for (RequiredCourseGap gap : requiredCourseGaps) {
            if (progressCalculator.offeringMatches(course, gap.course())) {
                fills.add("REQUIRED_COURSE:" + gap.course().courseName());
            }
        }
        return fills;
    }

    private void addCreditGaps(
            LinkedHashSet<String> fills,
            Set<String> gapCodes,
            CreditCategory category,
            boolean eligibleForCategory) {
        String categoryGap = category.gapCode();
        if (categoryGap != null
                && gapCodes.contains(categoryGap)
                && eligibleForCategory) {
            fills.add(categoryGap);
        }
        if (gapCodes.contains("TOTAL")) {
            fills.add("TOTAL");
        }
        if (category.isMajor()
                && eligibleForCategory
                && gapCodes.contains("PRIMARY_MAJOR")) {
            fills.add("PRIMARY_MAJOR");
        }
        if (category.isLiberal()
                && gapCodes.contains("LIBERAL_TOTAL")) {
            fills.add("LIBERAL_TOTAL");
        }
    }

    /**
     * courses.category가 "교양선택(제N영역:이름)" 형식으로 파싱되는 영역이 부족한
     * 교양 영역 중 하나와 일치하면 그 영역을 채우는 과목으로 추천한다.
     * (AcademicSectionQueryRepository의 자동편성용 파싱과 동일한 규칙을 공유한다.)
     */
    private void addAreaGap(
            LinkedHashSet<String> fills, Set<String> missingAreas, String category) {
        String liberalAreaCode = LiberalAreaCode.parse(category);
        if (liberalAreaCode != null && missingAreas.contains(liberalAreaCode)) {
            fills.add("AREA:" + liberalAreaCode);
        }
    }
}
