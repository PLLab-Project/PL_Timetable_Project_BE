package com.example.pl_timetable_project.optimization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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

@Schema(description = "OR-Tools 기반 시간표 자동편성 작업 생성 요청")
@Getter
@NoArgsConstructor
public class OptimizationCreateRequest {

    @Schema(description = "결과를 연결할 로그인 사용자의 시간표 ID", example = "12")
    @NotNull
    private Long timetableId;

    @Schema(description = "결과 시간표가 충족해야 할 최소 총학점", example = "12.0")
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal minCredits;

    @Schema(description = "결과 시간표가 넘을 수 없는 최대 총학점", example = "18.0")
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal maxCredits;

    @Schema(description = "점수 계산에서 우선적으로 맞출 목표 총학점", example = "15.0")
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal targetCredits;

    @Schema(
            description = "수업을 배치하지 않을 요일 집합",
            example = "[\"FRIDAY\"]",
            allowableValues = {
                    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                    "FRIDAY", "SATURDAY", "SUNDAY"
            })
    private Set<DayOfWeek> excludedDays = Set.of();

    @Schema(
            description = "모든 수업이 들어와야 하는 하루 수강 가능 시간 범위",
            example = "{\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\"}")
    @NotNull
    @Valid
    private TimeRangeRequest availableTime;

    @Schema(
            description = "수업 없이 확보할 점심시간 범위",
            example = "{\"startTime\":\"12:00:00\",\"endTime\":\"13:00:00\"}")
    @NotNull
    @Valid
    private TimeRangeRequest lunchTime;

    @Schema(description = "요일 하나에 배치할 수 있는 총 수업시간 상한(분)", example = "360")
    @NotNull
    @Min(1)
    private Integer maxDailyClassMinutes;

    @Schema(
            description = "학사 DB에 존재하는 후보 분반 목록. 최대 100개",
            example = "[{\"courseCode\":\"855121\",\"sectionCode\":\"01\",\"required\":true}]")
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
