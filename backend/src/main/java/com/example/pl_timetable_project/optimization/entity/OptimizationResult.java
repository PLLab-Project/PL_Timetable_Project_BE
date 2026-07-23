package com.example.pl_timetable_project.optimization.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "optimization_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptimizationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private OptimizationJob job;

    @Column(nullable = false)
    private Integer rank;

    @ElementCollection
    @CollectionTable(
            name = "optimization_result_course_slots",
            joinColumns = @JoinColumn(name = "result_id"))
    @OrderColumn(name = "position")
    private List<CourseSlot> courseSlots = new ArrayList<>();

    @Column(name = "attendance_days", nullable = false)
    private Integer attendanceDays;

    @Column(name = "total_credits", precision = 5, scale = 2, nullable = false)
    private BigDecimal totalCredits;

    @Column(name = "total_free_minutes", nullable = false)
    private Integer totalFreeMinutes;

    @Column(nullable = false)
    private Double score;

    public OptimizationResult(
            Integer rank,
            List<CourseSlot> courseSlots,
            Integer attendanceDays,
            BigDecimal totalCredits,
            Integer totalFreeMinutes,
            Double score) {
        this.rank = rank;
        this.courseSlots = new ArrayList<>(courseSlots);
        this.attendanceDays = attendanceDays;
        this.totalCredits = totalCredits;
        this.totalFreeMinutes = totalFreeMinutes;
        this.score = score;
    }

    void assignJob(OptimizationJob job) {
        this.job = job;
    }
}
