package com.example.pl_timetable_project.optimization.entity;

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
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자동 시간표 편성 작업. 후보 강의 목록은 영속화하지 않고(별도 Course 도메인이 없으므로)
 * 요청 시점에 넘어온 값을 그대로 편성 알고리즘에 흘려보내며, 이 엔티티는 조건과 상태만 관리한다.
 */
@Entity
@Table(name = "optimization_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptimizationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long timetableId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OptimizationJobStatus status;

    @Column(nullable = false)
    private Integer minCredit;

    @Column(nullable = false)
    private Integer maxCredit;

    @Column(nullable = false)
    private Integer targetCredit;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "optimization_job_excluded_days", joinColumns = @JoinColumn(name = "job_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private Set<DayOfWeek> excludedDays = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "optimization_job_required_courses", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "course_id")
    private Set<Long> requiredCourseIds = new HashSet<>();

    @Column(nullable = false)
    private LocalTime availableTimeStart;

    @Column(nullable = false)
    private LocalTime availableTimeEnd;

    @Column(nullable = false)
    private LocalTime lunchTimeStart;

    @Column(nullable = false)
    private LocalTime lunchTimeEnd;

    @Column(nullable = false)
    private Integer maxDailyClassMinutes;

    private String failureReason;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OptimizationResult> results = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OptimizationJob(Long userId, Long timetableId, Integer minCredit, Integer maxCredit, Integer targetCredit,
                            Set<DayOfWeek> excludedDays, Set<Long> requiredCourseIds,
                            LocalTime availableTimeStart, LocalTime availableTimeEnd,
                            LocalTime lunchTimeStart, LocalTime lunchTimeEnd, Integer maxDailyClassMinutes) {
        this.userId = userId;
        this.timetableId = timetableId;
        this.status = OptimizationJobStatus.PENDING;
        this.minCredit = minCredit;
        this.maxCredit = maxCredit;
        this.targetCredit = targetCredit;
        this.excludedDays = excludedDays == null ? new HashSet<>() : new HashSet<>(excludedDays);
        this.requiredCourseIds = requiredCourseIds == null ? new HashSet<>() : new HashSet<>(requiredCourseIds);
        this.availableTimeStart = availableTimeStart;
        this.availableTimeEnd = availableTimeEnd;
        this.lunchTimeStart = lunchTimeStart;
        this.lunchTimeEnd = lunchTimeEnd;
        this.maxDailyClassMinutes = maxDailyClassMinutes;
    }

    public void markProcessing() {
        this.status = OptimizationJobStatus.PROCESSING;
    }

    public void markSuccess(List<OptimizationResult> newResults) {
        this.results.clear();
        for (OptimizationResult result : newResults) {
            this.results.add(result);
            result.assignJob(this);
        }
        this.status = OptimizationJobStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        this.status = OptimizationJobStatus.FAILED;
        this.failureReason = reason;
    }

    public void markTimeout(String reason) {
        this.status = OptimizationJobStatus.TIMEOUT;
        this.failureReason = reason;
    }

    public void markCancelled() {
        this.status = OptimizationJobStatus.CANCELLED;
    }

    public boolean isFinished() {
        return this.status.isFinished();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
