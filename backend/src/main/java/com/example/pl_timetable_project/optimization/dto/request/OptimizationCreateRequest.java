package com.example.pl_timetable_project.optimization.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
    @Valid
    private TimeRangeRequest availableTime;

    @Schema(
            description = "중복 선택 가능한 하루 수강 가능 시간 범위. 있으면 availableTime보다 우선",
            example = "[{\"startTime\":\"09:00:00\",\"endTime\":\"12:00:00\"},"
                    + "{\"startTime\":\"13:00:00\",\"endTime\":\"18:00:00\"}]")
    @Valid
    private List<TimeRangeRequest> availableTimes = List.of();

    @Schema(
            description = "해당 요일·시간에는 어떤 강의도 배치하지 않는 공강 고정 조건",
            example = "[{\"dayOfWeek\":\"WEDNESDAY\","
                    + "\"startTime\":\"13:00:00\",\"endTime\":\"15:00:00\"}]")
    @Valid
    private List<BlockedTimeRequest> blockedTimes = List.of();

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
            description = "명시적 후보 분반 목록. 비우면 서버가 해당 학기의 전체 분반을 직접 조회",
            example = "[{\"courseCode\":\"855121\",\"sectionCode\":\"01\",\"required\":true}]")
    @Valid
    private List<CourseCandidateRequest> candidateCourses = List.of();

    @Schema(
            description = "서버가 후보를 직접 조회할 때 반드시 포함할 분반 목록",
            example = "[{\"courseCode\":\"855121\",\"sectionCode\":\"01\",\"required\":true}]")
    @Valid
    private List<CourseCandidateRequest> requiredCourses = List.of();

    @Schema(
            description = "사용자가 선택한 교양 영역 코드 목록. courses.category가 "
                    + "\"교양선택(제N영역:이름)\" 형태일 때의 \"제N영역:이름\" 값과 같아야 매칭된다. "
                    + "생략하거나 비우면 교양 영역 기반 가중치를 적용하지 않는다",
            example = "[\"제3영역:과학과기술\"]")
    private List<String> selectedLiberalAreas = List.of();

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
        this.availableTimes = List.of();
        this.blockedTimes = List.of();
        this.lunchTime = lunchTime;
        this.maxDailyClassMinutes = maxDailyClassMinutes;
        this.candidateCourses = candidateCourses == null ? List.of() : candidateCourses;
        this.requiredCourses = List.of();
        this.selectedLiberalAreas = List.of();
    }

    public OptimizationCreateRequest(
            Long timetableId,
            BigDecimal minCredits,
            BigDecimal maxCredits,
            BigDecimal targetCredits,
            Set<DayOfWeek> excludedDays,
            TimeRangeRequest availableTime,
            List<TimeRangeRequest> availableTimes,
            List<BlockedTimeRequest> blockedTimes,
            TimeRangeRequest lunchTime,
            Integer maxDailyClassMinutes,
            List<CourseCandidateRequest> candidateCourses,
            List<CourseCandidateRequest> requiredCourses) {
        this.timetableId = timetableId;
        this.minCredits = minCredits;
        this.maxCredits = maxCredits;
        this.targetCredits = targetCredits;
        this.excludedDays = excludedDays == null ? Set.of() : Set.copyOf(excludedDays);
        this.availableTime = availableTime;
        this.availableTimes = availableTimes == null ? List.of() : List.copyOf(availableTimes);
        this.blockedTimes = blockedTimes == null ? List.of() : List.copyOf(blockedTimes);
        this.lunchTime = lunchTime;
        this.maxDailyClassMinutes = maxDailyClassMinutes;
        this.candidateCourses =
                candidateCourses == null ? List.of() : List.copyOf(candidateCourses);
        this.requiredCourses =
                requiredCourses == null ? List.of() : List.copyOf(requiredCourses);
        this.selectedLiberalAreas = List.of();
    }
}
