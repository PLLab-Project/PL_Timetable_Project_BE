package com.example.pl_timetable_project.academic.graduation;

import java.math.BigDecimal;
import java.util.List;

public final class GraduationResponses {

    private GraduationResponses() {
    }

    public record Rule(
            String profileId,
            String datasetId,
            String sourceRuleId,
            int admissionYear,
            String academicUnit,
            String academicUnitKey,
            String academicUnitCode,
            String studentType,
            String programPath,
            CreditRequirements credits,
            LiberalRequirements liberalArts,
            List<AreaRequirement> liberalAreas,
            List<RequiredCourse> requiredCourses,
            boolean requiresManualReview,
            List<String> sourceRefs,
            List<Warning> warnings,
            List<NonAutomaticItem> nonAutomaticItems) {
    }

    public record CreditRequirements(
            int total,
            int majorFoundation,
            int majorRequired,
            int majorElective,
            int primaryMajor,
            Integer additionalMajor,
            Integer secondaryProgram) {
    }

    public record LiberalRequirements(
            int required,
            int elective,
            int totalMinimum,
            Integer totalMaximum) {
    }

    public record AreaRequirement(
            String area,
            int minimumCourses,
            Integer minimumCredits) {
    }

    public record RequiredCourse(
            String requirementType,
            String courseCode,
            String courseName,
            List<String> acceptedNames,
            BigDecimal credits,
            Integer recommendedGrade,
            String sourceRef) {
    }

    public record Warning(
            String code,
            String message,
            Integer calculated,
            Integer printed) {
    }

    public record NonAutomaticItem(
            String code,
            String title,
            String description,
            String sourceRef) {
    }

    public record Evaluation(
            String semesterId,
            Rule rule,
            CompletedCredits completedCredits,
            List<CreditGap> creditGaps,
            List<AreaGap> areaGaps,
            List<RequiredCourseGap> requiredCourseGaps,
            List<Recommendation> recommendations,
            boolean automaticRequirementsSatisfied,
            List<String> sourceRefs,
            List<Warning> warnings,
            List<NonAutomaticItem> nonAutomaticItems,
            SecondaryMajorEvaluation secondaryMajor) {
    }

    /**
     * 복수전공(DOUBLE_MAJOR) 학과의 졸업요건 판정 결과다. completedCredits/creditGaps가
     * 없다 — 이수과목 데이터에는 주전공/복수전공 중 어느 학점으로 잡히는지 구분하는
     * 정보가 없어, 학점은 이 학과 기준으로 자동 계산하지 않고 nonAutomaticItems로만
     * 안내한다. areaGaps/requiredCourseGaps는 특정 과목을 들었는지 여부만으로 판단
     * 가능해(전공 귀속과 무관) 정상적으로 자동 판정된다.
     */
    public record SecondaryMajorEvaluation(
            Rule rule,
            List<AreaGap> areaGaps,
            List<RequiredCourseGap> requiredCourseGaps,
            List<Recommendation> recommendations,
            boolean areaAndRequiredCoursesSatisfied,
            List<String> sourceRefs,
            List<Warning> warnings,
            List<NonAutomaticItem> nonAutomaticItems) {
    }

    public record CompletedCredits(
            BigDecimal total,
            BigDecimal majorFoundation,
            BigDecimal majorRequired,
            BigDecimal majorElective,
            BigDecimal primaryMajor,
            BigDecimal liberalRequired,
            BigDecimal liberalElective,
            BigDecimal liberalTotal) {
    }

    public record CreditGap(
            String code,
            String label,
            BigDecimal required,
            BigDecimal completed,
            BigDecimal missing) {
    }

    public record AreaGap(
            String area,
            int requiredCourses,
            int completedCourses,
            int missingCourses,
            BigDecimal requiredCredits,
            BigDecimal completedCredits,
            BigDecimal missingCredits) {
    }

    public record RequiredCourseGap(RequiredCourse course) {
    }

    public record Recommendation(
            String semesterId,
            String courseCode,
            String courseName,
            String category,
            BigDecimal credits,
            int sectionCount,
            List<String> fills) {
    }
}
