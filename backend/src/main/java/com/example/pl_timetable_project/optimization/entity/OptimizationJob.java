package com.example.pl_timetable_project.optimization.entity;

import com.example.pl_timetable_project.academic.section.SectionReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "optimization_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptimizationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "timetable_id", nullable = false)
    private Long timetableId;

    @Column(name = "semester_id", length = 20, nullable = false)
    private String semesterId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OptimizationJobStatus status;

    @Column(name = "min_credits", precision = 5, scale = 2, nullable = false)
    private BigDecimal minCredits;

    @Column(name = "max_credits", precision = 5, scale = 2, nullable = false)
    private BigDecimal maxCredits;

    @Column(name = "target_credits", precision = 5, scale = 2, nullable = false)
    private BigDecimal targetCredits;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "optimization_job_excluded_days",
            joinColumns = @JoinColumn(name = "job_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 20, nullable = false)
    private Set<DayOfWeek> excludedDays = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "optimization_job_required_sections",
            joinColumns = @JoinColumn(name = "job_id"))
    private Set<SectionReference> requiredSections = new HashSet<>();

    @Column(name = "available_start_minute", nullable = false)
    private Short availableStartMinute;

    @Column(name = "available_end_minute", nullable = false)
    private Short availableEndMinute;

    @Column(name = "lunch_start_minute", nullable = false)
    private Short lunchStartMinute;

    @Column(name = "lunch_end_minute", nullable = false)
    private Short lunchEndMinute;

    @Column(name = "max_daily_class_minutes", nullable = false)
    private Integer maxDailyClassMinutes;

    @Column(name = "failure_reason")
    private String failureReason;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OptimizationResult> results = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public OptimizationJob(
            UUID userId,
            Long timetableId,
            String semesterId,
            BigDecimal minCredits,
            BigDecimal maxCredits,
            BigDecimal targetCredits,
            Set<DayOfWeek> excludedDays,
            Set<SectionReference> requiredSections,
            LocalTime availableTimeStart,
            LocalTime availableTimeEnd,
            LocalTime lunchTimeStart,
            LocalTime lunchTimeEnd,
            Integer maxDailyClassMinutes) {
        this.userId = userId;
        this.timetableId = timetableId;
        this.semesterId = semesterId;
        this.status = OptimizationJobStatus.PENDING;
        this.minCredits = minCredits;
        this.maxCredits = maxCredits;
        this.targetCredits = targetCredits;
        this.excludedDays = excludedDays == null
                ? new HashSet<>() : new HashSet<>(excludedDays);
        this.requiredSections = requiredSections == null
                ? new HashSet<>() : new HashSet<>(requiredSections);
        this.availableStartMinute = toMinute(availableTimeStart);
        this.availableEndMinute = toMinute(availableTimeEnd);
        this.lunchStartMinute = toMinute(lunchTimeStart);
        this.lunchEndMinute = toMinute(lunchTimeEnd);
        this.maxDailyClassMinutes = maxDailyClassMinutes;
    }

    public void markProcessing() {
        status = OptimizationJobStatus.PROCESSING;
    }

    public void markSuccess(List<OptimizationResult> newResults) {
        results.clear();
        for (OptimizationResult result : newResults) {
            results.add(result);
            result.assignJob(this);
        }
        status = OptimizationJobStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        status = OptimizationJobStatus.FAILED;
        failureReason = reason;
    }

    public void markTimeout(String reason) {
        status = OptimizationJobStatus.TIMEOUT;
        failureReason = reason;
    }

    public void markCancelled() {
        status = OptimizationJobStatus.CANCELLED;
    }

    public boolean isFinished() {
        return status.isFinished();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    private static short toMinute(LocalTime time) {
        return (short) (time.getHour() * 60 + time.getMinute());
    }
}
