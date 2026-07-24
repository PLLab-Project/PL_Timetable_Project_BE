package com.example.pl_timetable_project.academic.graduation;

import com.example.pl_timetable_project.academic.common.TextQuery;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.AreaRule;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.CompletedCourse;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.ManualRule;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.OfferedCourse;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.RequiredCourseRule;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.RuleProfile;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.RuleScope;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.StudentScope;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.AreaGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.AreaRequirement;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.CompletedCredits;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.CreditGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.CreditRequirements;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Evaluation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.LiberalRequirements;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.NonAutomaticItem;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Recommendation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourse;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourseGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Rule;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Warning;
import com.example.pl_timetable_project.exception.AcademicResourceNotFoundException;
import com.example.pl_timetable_project.exception.InvalidAcademicQueryException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GraduationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Set<String> PROGRAM_PATHS = Set.of(
            "ADVANCED_MAJOR", "DOUBLE_MAJOR", "MINOR", "MICRO_MAJOR");

    private final GraduationQueryRepository repository;

    public GraduationService(GraduationQueryRepository repository) {
        this.repository = repository;
    }

    public Rule getRule(
            int admissionYear,
            String academicUnit,
            String studentType,
            String programPath) {
        RuleProfile profile = findProfile(new RuleScope(
                validateAdmissionYear(admissionYear),
                TextQuery.required(academicUnit, "학과"),
                normalizeToken(studentType, "학생 구분"),
                normalizeProgramPath(programPath)));
        return buildRule(profile);
    }

    public Evaluation evaluate(UUID userId, String semesterId) {
        StudentScope student = repository.findStudentScope(userId)
                .orElseThrow(() -> new AcademicResourceNotFoundException(
                        "졸업요건 판정에 필요한 학생 프로필을 찾을 수 없습니다."));
        RuleScope scope = scopeFrom(student);
        RuleProfile profile = findProfile(scope);
        Rule rule = buildRule(profile);
        List<CompletedCourse> courses = repository.findCompletedCourses(userId);
        CompletedCredits completed = summarizeCredits(courses);
        List<CreditGap> creditGaps = creditGaps(rule, completed);
        List<AreaGap> areaGaps = areaGaps(rule, courses);
        List<RequiredCourseGap> requiredCourseGaps =
                requiredCourseGaps(rule, courses);
        String evaluationSemester = repository.findEvaluationSemester(
                        TextQuery.optional(semesterId))
                .orElseThrow(() -> new AcademicResourceNotFoundException(
                        semesterId == null
                                ? "추천에 사용할 활성 학기를 찾을 수 없습니다."
                                : "학기를 찾을 수 없습니다. semesterId=" + semesterId));
        List<Recommendation> recommendations = recommendations(
                userId,
                evaluationSemester,
                profile,
                creditGaps,
                requiredCourseGaps);
        List<Warning> warnings = new ArrayList<>(rule.warnings());
        if (!areaGaps.isEmpty()) {
            warnings.add(new Warning(
                    "LIBERAL_AREA_RECOMMENDATION_REQUIRES_CATALOG_MAPPING",
                    "개설 강의 데이터에는 교양 영역이 없어 영역 부족분의 추천은 자동화하지 않습니다.",
                    null,
                    null));
        }
        return new Evaluation(
                evaluationSemester,
                rule,
                completed,
                creditGaps,
                areaGaps,
                requiredCourseGaps,
                recommendations,
                creditGaps.isEmpty()
                        && areaGaps.isEmpty()
                        && requiredCourseGaps.isEmpty(),
                rule.sourceRefs(),
                List.copyOf(warnings),
                rule.nonAutomaticItems());
    }

    private RuleProfile findProfile(RuleScope scope) {
        return repository.findRule(scope)
                .orElseThrow(() -> new AcademicResourceNotFoundException(
                        "조건에 맞는 졸업 학점 규칙을 찾을 수 없습니다. "
                                + "admissionYear=" + scope.admissionYear()
                                + ", academicUnit=" + scope.academicUnit()
                                + ", studentType=" + scope.studentType()
                                + ", programPath=" + scope.programPath()));
    }

    private Rule buildRule(RuleProfile profile) {
        List<AreaRequirement> areas = repository
                .findAreaRules(profile.liberalRequirementSetId())
                .stream()
                .map(rule -> new AreaRequirement(
                        rule.area(), rule.minCourses(), rule.minCredits()))
                .toList();
        List<RequiredCourse> requiredCourses = repository
                .findRequiredCourses(profile)
                .stream()
                .map(this::toRequiredCourse)
                .toList();
        LinkedHashSet<String> sourceRefs =
                new LinkedHashSet<>(repository.findSourceRefs(profile));
        if (profile.datasetSourcePath() != null
                && !profile.datasetSourcePath().isBlank()) {
            sourceRefs.add(profile.datasetSourcePath());
        }
        List<Warning> warnings = repository.findWarnings(profile).stream()
                .map(warning -> new Warning(
                        warning.code(),
                        "원문 인쇄값과 정규화 계산값이 다릅니다.",
                        warning.calculated(),
                        warning.printed()))
                .toList();
        List<NonAutomaticItem> manualItems = manualItems(profile);
        return new Rule(
                profile.id(),
                profile.datasetId(),
                profile.sourceRuleId(),
                profile.admissionYear(),
                profile.academicUnit(),
                profile.academicUnitKey(),
                profile.academicUnitCode(),
                profile.studentType(),
                profile.programPath(),
                new CreditRequirements(
                        profile.totalCreditsMin(),
                        profile.majorFoundationMin(),
                        profile.majorRequiredMin(),
                        profile.majorElectiveMin(),
                        profile.primaryMajorMin(),
                        profile.additionalMajorMin(),
                        profile.secondaryProgramMin()),
                new LiberalRequirements(
                        profile.liberalRequiredMin(),
                        profile.liberalElectiveMin(),
                        profile.liberalTotalMin(),
                        profile.liberalTotalMax()),
                areas,
                requiredCourses,
                profile.requiresManualReview() || !manualItems.isEmpty(),
                List.copyOf(sourceRefs),
                warnings,
                manualItems);
    }

    private List<NonAutomaticItem> manualItems(RuleProfile profile) {
        List<NonAutomaticItem> result = new ArrayList<>();
        if (profile.requiresManualReview()) {
            result.add(new NonAutomaticItem(
                    "PROFILE_MANUAL_REVIEW",
                    "공식 졸업요건 수동 확인",
                    "이 규칙은 원문 확인이 필요한 항목을 포함합니다.",
                    profile.datasetSourcePath()));
        }
        if (profile.additionalMajorMin() != null) {
            result.add(new NonAutomaticItem(
                    "ADDITIONAL_MAJOR_CREDITS",
                    "추가 전공 학점",
                    "이수과목 데이터에는 전공 귀속 구분이 없어 추가 전공 학점은 자동 판정하지 않습니다. 최소 "
                            + profile.additionalMajorMin() + "학점",
                    profile.datasetSourcePath()));
        }
        if (profile.secondaryProgramMin() != null) {
            result.add(new NonAutomaticItem(
                    "SECONDARY_PROGRAM_CREDITS",
                    "복수·부·마이크로전공 학점",
                    "이수과목 데이터에는 보조 전공 귀속 구분이 없어 자동 판정하지 않습니다. 최소 "
                            + profile.secondaryProgramMin() + "학점",
                    profile.datasetSourcePath()));
        }
        for (ManualRule manual : repository.findManualRules(profile)) {
            result.add(new NonAutomaticItem(
                    manual.code(),
                    manual.title(),
                    manual.description(),
                    manual.sourceRef()));
        }
        return List.copyOf(result);
    }

    private RequiredCourse toRequiredCourse(RequiredCourseRule rule) {
        return new RequiredCourse(
                rule.requirementType(),
                rule.courseCode(),
                rule.courseName(),
                rule.acceptedNames(),
                rule.credits(),
                rule.recommendedGrade(),
                rule.sourceRef());
    }

    private StudentScope requireComplete(StudentScope student) {
        if (student.admissionYear() == null
                || TextQuery.optional(student.academicUnitKey()) == null
                || TextQuery.optional(student.studentType()) == null
                || TextQuery.optional(student.programPath()) == null) {
            throw new InvalidAcademicQueryException(
                    "학생 프로필의 입학연도, 학과, 학생 구분, 전공 방식이 모두 필요합니다.");
        }
        return student;
    }

    private RuleScope scopeFrom(StudentScope rawStudent) {
        StudentScope student = requireComplete(rawStudent);
        return new RuleScope(
                validateAdmissionYear(student.admissionYear()),
                student.academicUnitKey(),
                normalizeToken(student.studentType(), "학생 구분"),
                normalizeProgramPath(student.programPath()));
    }

    private CompletedCredits summarizeCredits(List<CompletedCourse> courses) {
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
                case MAJOR_REQUIRED ->
                        majorRequired = majorRequired.add(credits);
                case MAJOR_ELECTIVE ->
                        majorElective = majorElective.add(credits);
                case LIBERAL_REQUIRED ->
                        liberalRequired = liberalRequired.add(credits);
                case LIBERAL_ELECTIVE ->
                        liberalElective = liberalElective.add(credits);
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

    private List<CreditGap> creditGaps(
            Rule rule, CompletedCredits completed) {
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

    private List<AreaGap> areaGaps(
            Rule rule, List<CompletedCourse> courses) {
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

    private List<RequiredCourseGap> requiredCourseGaps(
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

    private boolean matches(
            Set<String> completedKeys, RequiredCourse required) {
        if (hasCourseKey(completedKeys, required.courseCode())
                || hasCourseKey(completedKeys, required.courseName())) {
            return true;
        }
        return required.acceptedNames().stream()
                .anyMatch(name -> hasCourseKey(completedKeys, name));
    }

    private List<Recommendation> recommendations(
            UUID userId,
            String semesterId,
            RuleProfile profile,
            List<CreditGap> creditGaps,
            List<RequiredCourseGap> requiredCourseGaps) {
        Set<String> gapCodes = creditGaps.stream()
                .map(CreditGap::code)
                .collect(java.util.stream.Collectors.toSet());
        List<Recommendation> result = new ArrayList<>();
        for (OfferedCourse course : repository.findOfferedCourses(
                userId, semesterId, profile.academicUnitCode())) {
            LinkedHashSet<String> fills = new LinkedHashSet<>();
            for (RequiredCourseGap gap : requiredCourseGaps) {
                if (offeringMatches(course, gap.course())) {
                    fills.add("REQUIRED_COURSE:" + gap.course().courseName());
                }
            }
            CreditCategory category = classify(course.category());
            String creditGap = category.gapCode();
            boolean eligibleForCategory = category.isLiberal()
                    || profile.academicUnitCode() == null
                    || course.academicUnitMatch();
            if (creditGap != null
                    && gapCodes.contains(creditGap)
                    && eligibleForCategory) {
                fills.add(creditGap);
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
                .limit(20)
                .toList();
    }

    private boolean offeringMatches(
            OfferedCourse offered, RequiredCourse required) {
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

    private int validateAdmissionYear(int admissionYear) {
        if (admissionYear < 1900 || admissionYear > 2100) {
            throw new InvalidAcademicQueryException(
                    "입학연도는 1900 이상 2100 이하여야 합니다.");
        }
        return admissionYear;
    }

    private String normalizeProgramPath(String programPath) {
        String normalized = normalizeToken(programPath, "전공 방식");
        if (!PROGRAM_PATHS.contains(normalized)) {
            throw new InvalidAcademicQueryException(
                    "programPath는 ADVANCED_MAJOR, DOUBLE_MAJOR, MINOR, "
                            + "MICRO_MAJOR 중 하나여야 합니다.");
        }
        return normalized;
    }

    private String normalizeToken(String value, String fieldName) {
        return TextQuery.required(value, fieldName).toUpperCase(Locale.ROOT);
    }

    private CreditCategory classify(String rawCategory) {
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

    private enum CreditCategory {
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
