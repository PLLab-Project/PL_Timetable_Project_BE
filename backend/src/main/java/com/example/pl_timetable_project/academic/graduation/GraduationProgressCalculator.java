package com.example.pl_timetable_project.academic.graduation;

import com.example.pl_timetable_project.academic.common.TextQuery;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.CompletedCourse;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.OfferedCourse;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.AreaGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.AreaRequirement;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.CompletedCredits;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.CreditGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourse;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourseGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Rule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 이수 내역을 학점·교양영역·필수과목 충족 상태로 계산합니다. */
@Component
public class GraduationProgressCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    CompletedCredits summarizeCredits(List<CompletedCourse> courses) {
        BigDecimal total = ZERO;
        BigDecimal foundation = ZERO;
        BigDecimal majorRequired = ZERO;
        BigDecimal majorElective = ZERO;
        BigDecimal liberalRequired = ZERO;
        BigDecimal liberalElective = ZERO;
        for (CompletedCourse course : courses) {
            BigDecimal credits = nonNull(course.credits());
            total = total.add(credits);
            switch (classify(course.category())) {
                case MAJOR_FOUNDATION -> foundation = foundation.add(credits);
                case MAJOR_REQUIRED -> majorRequired = majorRequired.add(credits);
                case MAJOR_ELECTIVE -> majorElective = majorElective.add(credits);
                case LIBERAL_REQUIRED -> liberalRequired = liberalRequired.add(credits);
                case LIBERAL_ELECTIVE -> liberalElective = liberalElective.add(credits);
                case OTHER -> {
                }
            }
        }
        return new CompletedCredits(
                total,
                foundation,
                majorRequired,
                majorElective,
                foundation.add(majorRequired).add(majorElective),
                liberalRequired,
                liberalElective,
                liberalRequired.add(liberalElective));
    }

    List<CreditGap> creditGaps(Rule rule, CompletedCredits completed) {
        List<CreditGap> result = new ArrayList<>();
        addGap(result, "TOTAL", "총학점",
                rule.credits().total(), completed.total());
        addGap(result, "MAJOR_FOUNDATION", "전공기초",
                rule.credits().majorFoundation(), completed.majorFoundation());
        addGap(result, "MAJOR_REQUIRED", "전공필수",
                rule.credits().majorRequired(), completed.majorRequired());
        addGap(result, "MAJOR_ELECTIVE", "전공선택",
                rule.credits().majorElective(), completed.majorElective());
        addGap(result, "PRIMARY_MAJOR", "주전공 합계",
                rule.credits().primaryMajor(), completed.primaryMajor());
        addGap(result, "LIBERAL_REQUIRED", "교양필수",
                rule.liberalArts().required(), completed.liberalRequired());
        addGap(result, "LIBERAL_ELECTIVE", "교양선택",
                rule.liberalArts().elective(), completed.liberalElective());
        addGap(result, "LIBERAL_TOTAL", "교양 합계",
                rule.liberalArts().totalMinimum(), completed.liberalTotal());
        return List.copyOf(result);
    }

    List<AreaGap> areaGaps(Rule rule, List<CompletedCourse> courses) {
        Map<String, AreaProgress> progress = new HashMap<>();
        for (CompletedCourse course : courses) {
            if (!classify(course.category()).isLiberal()
                    || TextQuery.optional(course.area()) == null) {
                continue;
            }
            String key = normalizeKey(course.area());
            AreaProgress current = progress.getOrDefault(
                    key, new AreaProgress(0, ZERO));
            progress.put(key, new AreaProgress(
                    current.courseCount() + 1,
                    current.credits().add(nonNull(course.credits()))));
        }
        List<AreaGap> result = new ArrayList<>();
        for (AreaRequirement area : rule.liberalAreas()) {
            AreaProgress current = progress.getOrDefault(
                    normalizeKey(area.area()), new AreaProgress(0, ZERO));
            int missingCourses = Math.max(
                    area.minimumCourses() - current.courseCount(), 0);
            BigDecimal requiredCredits = area.minimumCredits() == null
                    ? ZERO
                    : BigDecimal.valueOf(area.minimumCredits());
            BigDecimal missingCredits = requiredCredits.subtract(
                    current.credits()).max(ZERO);
            if (missingCourses > 0 || missingCredits.signum() > 0) {
                result.add(new AreaGap(
                        area.area(),
                        area.minimumCourses(),
                        current.courseCount(),
                        missingCourses,
                        requiredCredits,
                        current.credits(),
                        missingCredits));
            }
        }
        return List.copyOf(result);
    }

    List<RequiredCourseGap> requiredCourseGaps(
            Rule rule, List<CompletedCourse> courses) {
        Set<String> completedKeys = new HashSet<>();
        for (CompletedCourse course : courses) {
            addCourseKeys(completedKeys, course.courseCode(), course.courseName());
        }
        return rule.requiredCourses().stream()
                .filter(required -> !matches(completedKeys, required))
                .map(RequiredCourseGap::new)
                .toList();
    }

    boolean offeringMatches(OfferedCourse offered, RequiredCourse required) {
        String offeredCode = normalizeKey(offered.courseCode());
        String offeredName = normalizeKey(offered.courseName());
        if ((!offeredCode.isEmpty()
                && offeredCode.equals(normalizeKey(required.courseCode())))
                || offeredName.equals(normalizeKey(required.courseName()))) {
            return true;
        }
        return required.acceptedNames().stream()
                .map(this::normalizeKey)
                .anyMatch(offeredName::equals);
    }

    CreditCategory classify(String rawCategory) {
        String category = normalizeKey(rawCategory);
        if (category.contains("전공기초")
                || category.equals("전기")
                || category.contains("majorfoundation")) {
            return CreditCategory.MAJOR_FOUNDATION;
        }
        if (category.contains("전공필수")
                || category.equals("전필")
                || category.contains("majorrequired")) {
            return CreditCategory.MAJOR_REQUIRED;
        }
        if (category.contains("전공선택")
                || category.equals("전선")
                || category.contains("majorelective")) {
            return CreditCategory.MAJOR_ELECTIVE;
        }
        if (category.contains("교양필수")
                || category.equals("교필")
                || category.contains("liberalrequired")) {
            return CreditCategory.LIBERAL_REQUIRED;
        }
        if (category.contains("교양")
                || category.equals("교선")
                || category.contains("liberalelective")) {
            return CreditCategory.LIBERAL_ELECTIVE;
        }
        return CreditCategory.OTHER;
    }

    private void addGap(
            List<CreditGap> gaps,
            String code,
            String label,
            int required,
            BigDecimal completed) {
        BigDecimal requiredCredits = BigDecimal.valueOf(required);
        BigDecimal missing = requiredCredits.subtract(completed);
        if (missing.signum() > 0) {
            gaps.add(new CreditGap(
                    code, label, requiredCredits, completed, missing));
        }
    }

    private boolean matches(
            Set<String> completedKeys, RequiredCourse required) {
        if (hasCourseKey(completedKeys, required.courseCode())
                || hasCourseKey(completedKeys, required.courseName())) {
            return true;
        }
        return required.acceptedNames().stream()
                .anyMatch(name -> hasCourseKey(completedKeys, name));
    }

    private void addCourseKeys(
            Set<String> keys, String courseCode, String courseName) {
        if (TextQuery.optional(courseCode) != null) {
            keys.add(normalizeKey(courseCode));
        }
        if (TextQuery.optional(courseName) != null) {
            keys.add(normalizeKey(courseName));
        }
    }

    private boolean hasCourseKey(Set<String> keys, String value) {
        String key = normalizeKey(value);
        return !key.isEmpty() && keys.contains(key);
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private record AreaProgress(int courseCount, BigDecimal credits) {
    }

    enum CreditCategory {
        MAJOR_FOUNDATION("MAJOR_FOUNDATION", true, false),
        MAJOR_REQUIRED("MAJOR_REQUIRED", true, false),
        MAJOR_ELECTIVE("MAJOR_ELECTIVE", true, false),
        LIBERAL_REQUIRED("LIBERAL_REQUIRED", false, true),
        LIBERAL_ELECTIVE("LIBERAL_ELECTIVE", false, true),
        OTHER(null, false, false);

        private final String gapCode;
        private final boolean major;
        private final boolean liberal;

        CreditCategory(String gapCode, boolean major, boolean liberal) {
            this.gapCode = gapCode;
            this.major = major;
            this.liberal = liberal;
        }

        String gapCode() {
            return gapCode;
        }

        boolean isLiberal() {
            return liberal;
        }

        boolean isMajor() {
            return major;
        }
    }
}
