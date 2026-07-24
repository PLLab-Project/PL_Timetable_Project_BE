package com.example.pl_timetable_project.optimization.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    @Min(0)
    private Integer minCredit;

    @NotNull
    @Min(0)
    private Integer maxCredit;

    @NotNull
    @Min(0)
    private Integer targetCredit;

    private Set<DayOfWeek> excludedDays = Set.of();

    private Set<Long> requiredCourseIds = Set.of();

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

    public OptimizationCreateRequest(Long timetableId, Integer minCredit, Integer maxCredit, Integer targetCredit,
                                      Set<DayOfWeek> excludedDays, Set<Long> requiredCourseIds,
                                      TimeRangeRequest availableTime, TimeRangeRequest lunchTime,
                                      Integer maxDailyClassMinutes, List<CourseCandidateRequest> candidateCourses) {
        this.timetableId = timetableId;
        this.minCredit = minCredit;
        this.maxCredit = maxCredit;
        this.targetCredit = targetCredit;
        this.excludedDays = excludedDays == null ? Set.of() : excludedDays;
        this.requiredCourseIds = requiredCourseIds == null ? Set.of() : requiredCourseIds;
        this.availableTime = availableTime;
        this.lunchTime = lunchTime;
        this.maxDailyClassMinutes = maxDailyClassMinutes;
        this.candidateCourses = candidateCourses;
    }
}
