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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private String semester;

    @OneToMany(mappedBy = "timetable", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimetableCourse> timetableCourses = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Timetable(Long userId, String name, Integer year, String semester) {
        this.userId = userId;
        this.name = name;
        this.year = year;
        this.semester = semester;
    }

    public void updateInfo(String name, Integer year, String semester) {
        if (name != null) {
            this.name = name;
        }
        if (year != null) {
            this.year = year;
        }
        if (semester != null) {
            this.semester = semester;
        }
    }

    public void addCourse(TimetableCourse course) {
        this.timetableCourses.add(course);
        course.assignTimetable(this);
    }

    public void removeCourse(TimetableCourse course) {
        this.timetableCourses.remove(course);
    }

    public void clearCourses() {
        this.timetableCourses.clear();
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
