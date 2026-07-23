package com.example.pl_timetable_project.timetable.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "timetables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Timetable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "semester_id", length = 20, nullable = false, updatable = false)
    private String semesterId;

    @Column(length = 120, nullable = false)
    private String name;

    @OneToMany(mappedBy = "timetable", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimetableCourse> timetableCourses = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Timetable(UUID userId, String semesterId, String name) {
        this.userId = userId;
        this.semesterId = semesterId;
        this.name = name;
    }

    public void rename(String name) {
        if (name != null) {
            this.name = name;
        }
    }

    public void addCourse(TimetableCourse course) {
        timetableCourses.add(course);
        course.assignTimetable(this);
    }

    public void removeCourse(TimetableCourse course) {
        timetableCourses.remove(course);
    }

    public void clearCourses() {
        timetableCourses.clear();
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
