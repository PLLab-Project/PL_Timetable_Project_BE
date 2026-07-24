package com.example.pl_timetable_project.academic.graduation;

import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.ManualRule;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.RequiredCourseRule;
import com.example.pl_timetable_project.academic.graduation.GraduationQueryRepository.RuleProfile;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.AreaRequirement;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.CreditRequirements;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.LiberalRequirements;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.NonAutomaticItem;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.RequiredCourse;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Rule;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Warning;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

/** 정규화된 졸업요건 조회 결과를 API 규칙 응답으로 조립합니다. */
@Component
public class GraduationRuleAssembler {

    private final GraduationQueryRepository repository;

    public GraduationRuleAssembler(GraduationQueryRepository repository) {
        this.repository = repository;
    }

    Rule assemble(RuleProfile profile) {
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
}
