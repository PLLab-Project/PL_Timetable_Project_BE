package com.example.pl_timetable_project.optimization.dto.response;

import com.example.pl_timetable_project.optimization.entity.OptimizationResult;
import java.util.List;
import lombok.Getter;

@Getter
public class OptimizationResultResponse {

    private final Integer rank;
    private final List<OptimizationCourseSlotResponse> sections;
    private final Integer attendanceDays;
    private final Integer totalCredits;
    private final Integer totalFreeMinutes;
    private final Double score;

    private OptimizationResultResponse(Integer rank, List<OptimizationCourseSlotResponse> sections,
                                        Integer attendanceDays, Integer totalCredits,
                                        Integer totalFreeMinutes, Double score) {
        this.rank = rank;
        this.sections = sections;
        this.attendanceDays = attendanceDays;
        this.totalCredits = totalCredits;
        this.totalFreeMinutes = totalFreeMinutes;
        this.score = score;
    }

    public static OptimizationResultResponse from(OptimizationResult result) {
        return new OptimizationResultResponse(
                result.getRank(),
                result.getCourseSlots().stream().map(OptimizationCourseSlotResponse::from).toList(),
                result.getAttendanceDays(),
                result.getTotalCredits(),
                result.getTotalFreeMinutes(),
                result.getScore());
    }
}
