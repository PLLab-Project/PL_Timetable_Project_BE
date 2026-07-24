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
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Recommendation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourseGap;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Rule;
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
        RuleProfile profile = findProfile(scopeFrom(student));
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
        List<Recommendation> recommendations = recommendationService.recommend(
                userId,
                evaluationSemester,
                profile,
                creditGaps,
                requiredCourseGaps);
        List<Warning> warnings = evaluationWarnings(rule, areaGaps);

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
                warnings,
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

    private RuleScope scopeFrom(StudentScope rawStudent) {
        StudentScope student = requireComplete(rawStudent);
        return new RuleScope(
                validateAdmissionYear(student.admissionYear()),
                student.academicUnitKey(),
                normalizeToken(student.studentType(), "학생 구분"),
                normalizeProgramPath(student.programPath()));
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

    private List<Warning> evaluationWarnings(
            Rule rule, List<AreaGap> areaGaps) {
        List<Warning> warnings = new ArrayList<>(rule.warnings());
        if (!areaGaps.isEmpty()) {
            warnings.add(new Warning(
                    "LIBERAL_AREA_RECOMMENDATION_REQUIRES_CATALOG_MAPPING",
                    "개설 강의 데이터에는 교양 영역이 없어 영역 부족분의 추천은 자동화하지 않습니다.",
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
