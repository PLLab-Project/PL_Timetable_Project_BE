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
