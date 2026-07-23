package com.example.pl_timetable_project.optimization.dto.response;

import com.example.pl_timetable_project.optimization.entity.OptimizationResult;
import java.math.BigDecimal;
import java.util.List;

public record OptimizationResultResponse(
        Integer rank,
        List<OptimizationCourseSlotResponse> sections,
        Integer attendanceDays,
        BigDecimal totalCredits,
        Integer totalFreeMinutes,
        Double score) {

    public static OptimizationResultResponse from(OptimizationResult result) {
        return new OptimizationResultResponse(
                result.getRank(),
                result.getCourseSlots().stream()
                        .map(OptimizationCourseSlotResponse::from)
                        .toList(),
                result.getAttendanceDays(),
                result.getTotalCredits(),
                result.getTotalFreeMinutes(),
                result.getScore());
    }
}
