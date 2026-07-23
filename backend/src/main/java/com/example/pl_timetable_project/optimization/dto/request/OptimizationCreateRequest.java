package com.example.pl_timetable_project.optimization.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OptimizationCreateRequest {

    @NotNull
    private Long timetableId;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal minCredits;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal maxCredits;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal targetCredits;

    private Set<DayOfWeek> excludedDays = Set.of();

    @NotNull
    @Valid
    private TimeRangeRequest availableTime;

    @NotNull
    @Valid
    private TimeRangeRequest lunchTime;

    @NotNull
    @Min(1)
    private Integer maxDailyClassMinutes;

    @NotEmpty
    @Valid
    private List<CourseCandidateRequest> candidateCourses;

    public OptimizationCreateRequest(
            Long timetableId,
            BigDecimal minCredits,
            BigDecimal maxCredits,
            BigDecimal targetCredits,
            Set<DayOfWeek> excludedDays,
            TimeRangeRequest availableTime,
            TimeRangeRequest lunchTime,
            Integer maxDailyClassMinutes,
            List<CourseCandidateRequest> candidateCourses) {
        this.timetableId = timetableId;
        this.minCredits = minCredits;
        this.maxCredits = maxCredits;
        this.targetCredits = targetCredits;
        this.excludedDays = excludedDays == null ? Set.of() : Set.copyOf(excludedDays);
        this.availableTime = availableTime;
        this.lunchTime = lunchTime;
        this.maxDailyClassMinutes = maxDailyClassMinutes;
        this.candidateCourses = candidateCourses;
    }
}
