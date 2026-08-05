package com.example.pl_timetable_project.academic.graduation;

import com.example.pl_timetable_project.academic.common.TextQuery;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.CompletedCourse;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.RuleProfile;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.RuleScope;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.StudentScope;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.AreaGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.CompletedCredits;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.CreditGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Evaluation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.NonAutomaticItem;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Recommendation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourseGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Rule;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.SecondaryMajorEvaluation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Warning;
import com.example.pl_timetable_project.exception.AcademicResourceNotFoundException;
import com.example.pl_timetable_project.exception.InvalidAcademicQueryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 졸업요건 조회와 개인 판정 흐름을 조정하는 애플리케이션 서비스입니다. */
@Service
@Transactional(readOnly = true)
public class GraduationService {

    private static final Set<String> PROGRAM_PATHS = Set.of(
            "ADVANCED_MAJOR", "DOUBLE_MAJOR", "MINOR", "MICRO_MAJOR");

    private final GraduationQueryRepository repository;
    private final GraduationRuleAssembler ruleAssembler;
    private final GraduationProgressCalculator progressCalculator;
    private final GraduationRecommendationService recommendationService;

    public GraduationService(
            GraduationQueryRepository repository,
            GraduationRuleAssembler ruleAssembler,
            GraduationProgressCalculator progressCalculator,
            GraduationRecommendationService recommendationService) {
        this.repository = repository;
        this.ruleAssembler = ruleAssembler;
        this.progressCalculator = progressCalculator;
        this.recommendationService = recommendationService;
    }

    public Rule getRule(
            int admissionYear,
            String academicUnit,
            String studentType,
            String programPath) {
        RuleScope scope = new RuleScope(
                validateAdmissionYear(admissionYear),
                TextQuery.required(academicUnit, "학과"),
                normalizeToken(studentType, "학생 구분"),
                normalizeProgramPath(programPath));
        return ruleAssembler.assemble(findProfile(scope));
    }

    public Evaluation evaluate(UUID userId, String semesterId) {
        StudentScope student = repository.findStudentScope(userId)
                .orElseThrow(() -> new AcademicResourceNotFoundException(
                        "졸업요건 판정에 필요한 학생 프로필을 찾을 수 없습니다."));
        StudentRuleScopes scopes = scopeFrom(student);
        RuleProfile profile = findProfile(scopes.primary());
        Rule rule = ruleAssembler.assemble(profile);
        List<CompletedCourse> courses = repository.findCompletedCourses(userId);
        CompletedCredits completed = progressCalculator.summarizeCredits(courses);
        List<CreditGap> creditGaps =
                progressCalculator.creditGaps(rule, completed);
        List<AreaGap> areaGaps =
                progressCalculator.areaGaps(rule, courses);
        List<RequiredCourseGap> requiredCourseGaps =
                progressCalculator.requiredCourseGaps(rule, courses);
        String evaluationSemester = resolveEvaluationSemester(semesterId);
        Set<String> offeredLiberalAreas =
                repository.findOfferedLiberalAreaCodes(evaluationSemester);
        List<Recommendation> recommendations = recommendationService.recommend(
                userId,
                evaluationSemester,
                profile,
                creditGaps,
                areaGaps,
                requiredCourseGaps);
        List<Warning> warnings = new ArrayList<>(
                evaluationWarnings(rule, areaGaps, offeredLiberalAreas));
        SecondaryMajorEvaluation secondaryEvaluation = scopes.secondary() == null
                ? null
                : evaluateSecondaryMajor(
                        userId, evaluationSemester, scopes.secondary(), courses,
                        offeredLiberalAreas, warnings);

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
                rule.nonAutomaticItems(),
                secondaryEvaluation);
    }

    /**
     * 복수전공 학과의 졸업요건을 별도로 판정한다. completed_courses에는 이수과목이
     * 주전공/복수전공 중 어느 학점으로 귀속되는지 구분하는 데이터가 없어(지난 세션
     * 확인 사항) 학점(completedCredits/creditGaps)은 이 학과 기준으로 자동 계산하지
     * 않고 NonAutomaticItem으로만 안내한다. 반면 영역·필수과목 이수 여부는 그 과목을
     * 들었는지 여부만으로 판단 가능해(전공 귀속과 무관) 그대로 자동 판정한다.
     * 복수전공 학과의 규칙 데이터 자체가 카탈로그에 없으면(데이터 커버리지 문제)
     * 전체 판정을 실패시키지 않고 상위 warnings에 안내만 남긴 뒤 null을 반환한다.
     */
    private SecondaryMajorEvaluation evaluateSecondaryMajor(
            UUID userId,
            String evaluationSemester,
            RuleScope secondaryScope,
            List<CompletedCourse> courses,
            Set<String> offeredLiberalAreas,
            List<Warning> topLevelWarnings) {
        RuleProfile profile;
        try {
            profile = findProfile(secondaryScope);
        } catch (AcademicResourceNotFoundException notFound) {
            topLevelWarnings.add(new Warning(
                    "SECONDARY_MAJOR_RULE_NOT_FOUND",
                    "복수전공 학과의 졸업요건 데이터를 찾을 수 없어 복수전공 판정은 건너뜁니다. "
                            + "academicUnit=" + secondaryScope.academicUnit(),
                    null,
                    null));
            return null;
        }
        Rule rule = ruleAssembler.assemble(profile);
        List<AreaGap> areaGaps = progressCalculator.areaGaps(rule, courses);
        List<RequiredCourseGap> requiredCourseGaps =
                progressCalculator.requiredCourseGaps(rule, courses);
        List<Recommendation> recommendations = recommendationService.recommend(
                userId, evaluationSemester, profile, List.of(), areaGaps, requiredCourseGaps);
        List<NonAutomaticItem> nonAutomaticItems =
                new ArrayList<>(rule.nonAutomaticItems());
        nonAutomaticItems.add(new NonAutomaticItem(
                "SECONDARY_MAJOR_CREDITS_NOT_AUTOMATED",
                "복수전공 학점 자동 판정 불가",
                "이수과목 데이터에는 주전공/복수전공 귀속 구분이 없어 복수전공 기준 학점은 자동 "
                        + "계산하지 않습니다. 영역·필수과목 이수 여부만 자동 판정됩니다.",
                profile.datasetSourcePath()));
        return new SecondaryMajorEvaluation(
                rule,
                areaGaps,
                requiredCourseGaps,
                recommendations,
                areaGaps.isEmpty() && requiredCourseGaps.isEmpty(),
                rule.sourceRefs(),
                evaluationWarnings(rule, areaGaps, offeredLiberalAreas),
                List.copyOf(nonAutomaticItems));
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

    private StudentRuleScopes scopeFrom(StudentScope rawStudent) {
        StudentScope student = requireComplete(rawStudent);
        RuleScope primary = new RuleScope(
                validateAdmissionYear(student.admissionYear()),
                student.academicUnitKey(),
                normalizeToken(student.studentType(), "학생 구분"),
                normalizeProgramPath(student.programPath()));
        RuleScope secondary = TextQuery.optional(student.secondaryAcademicUnitKey()) == null
                ? null
                : new RuleScope(
                        primary.admissionYear(),
                        student.secondaryAcademicUnitKey(),
                        primary.studentType(),
                        "DOUBLE_MAJOR");
        return new StudentRuleScopes(primary, secondary);
    }

    /**
     * 주전공 규칙 조회 범위(primary)와, 복수전공(DOUBLE_MAJOR)이 있을 때만 채워지는
     * 복수전공 학과의 규칙 조회 범위(secondary)를 함께 담는다. secondary는
     * student_academic_programs에 DOUBLE_MAJOR 행이 없으면 null이다.
     */
    private record StudentRuleScopes(RuleScope primary, RuleScope secondary) {
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

    private String resolveEvaluationSemester(String semesterId) {
        return repository.findEvaluationSemester(TextQuery.optional(semesterId))
                .orElseThrow(() -> new AcademicResourceNotFoundException(
                        semesterId == null
                                ? "추천에 사용할 활성 학기를 찾을 수 없습니다."
                                : "학기를 찾을 수 없습니다. semesterId=" + semesterId));
    }

    /**
     * areaGaps 중 이번 학기 개설 과목이 아예 없는 영역이 있을 때만 경고를 남긴다.
     * courses.category가 "교양선택(제N영역:이름)"으로 파싱 가능해 실제로 매칭
     * 추천이 가능한 경우(GraduationRecommendationService.recommend())에는
     * 더 이상 이 경고를 띄우지 않는다.
     */
    private List<Warning> evaluationWarnings(
            Rule rule, List<AreaGap> areaGaps, Set<String> offeredLiberalAreas) {
        List<Warning> warnings = new ArrayList<>(rule.warnings());
        List<String> areasWithoutOfferings = areaGaps.stream()
                .map(AreaGap::area)
                .filter(area -> !offeredLiberalAreas.contains(area))
                .toList();
        if (!areasWithoutOfferings.isEmpty()) {
            warnings.add(new Warning(
                    "LIBERAL_AREA_RECOMMENDATION_REQUIRES_CATALOG_MAPPING",
                    "해당 학기에 개설된 과목이 없어 추천이 불가능한 교양 영역이 있습니다: "
                            + String.join(", ", areasWithoutOfferings),
                    null,
                    null));
        }
        return List.copyOf(warnings);
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
}
