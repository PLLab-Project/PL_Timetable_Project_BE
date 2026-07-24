package com.example.pl_timetable_project.completedcourse.entity;

import com.example.pl_timetable_project.completedcourse.CompletedCourseInputSource;
import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "completed_courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompletedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "course_code", length = 40)
    private String courseCode;

    @Column(name = "course_name", length = 240, nullable = false)
    private String courseName;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal credits;

    @Column(length = 160, nullable = false)
    private String category;

    @Column(length = 120)
    private String area;

    @Column(length = 20)
    private String semester;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    private CompletedCourseStatus status;

    @Column(name = "historical_offering_id", length = 36, updatable = false)
    private String historicalOfferingId;

    @Column(name = "section_code", length = 40, updatable = false)
    private String sectionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_source", length = 32, nullable = false, updatable = false)
    private CompletedCourseInputSource inputSource;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_snapshot", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> sourceSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CompletedCourse(
            UUID userId,
            String courseCode,
            String courseName,
            BigDecimal credits,
            String category,
            String area,
            String semester,
            CompletedCourseStatus status,
            String historicalOfferingId,
            String sectionCode,
            CompletedCourseInputSource inputSource,
            Map<String, Object> sourceSnapshot) {
        this.userId = userId;
        this.courseCode = courseCode;
        this.courseName = courseName;
        this.credits = credits;
        this.category = category;
        this.area = area;
        this.semester = semester;
        this.status = status;
        this.historicalOfferingId = historicalOfferingId;
        this.sectionCode = sectionCode;
        this.inputSource = inputSource;
        this.sourceSnapshot = sourceSnapshot;
    }

    public void update(
            String courseCode,
            String courseName,
            BigDecimal credits,
            String category,
            String area,
            String semester,
            CompletedCourseStatus status) {
        if (courseCode != null) {
            this.courseCode = courseCode;
        }
        if (courseName != null) {
            this.courseName = courseName;
        }
        if (credits != null) {
            this.credits = credits;
        }
        if (category != null) {
            this.category = category;
        }
        if (area != null) {
            this.area = area;
        }
        if (semester != null) {
            this.semester = semester;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public void markCompleted() {
        status = CompletedCourseStatus.COMPLETED;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
