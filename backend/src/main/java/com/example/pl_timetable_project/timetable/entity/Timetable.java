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
import jakarta.persistence.Version;
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

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite;

    /**
     * 낙관적 잠금 버전. timetableCourses 컬렉션 변경(추가/삭제/전체 교체)도 이
     * 버전을 증가시킨다 — Timetable과 그 분반 목록을 하나의 집합체로 보호해,
     * 같은 시간표를 동시에 수정하는 두 요청 중 하나가 ObjectOptimisticLockingFailureException으로
     * 감지되도록 한다(GlobalExceptionHandler가 409로 응답).
     */
    @Version
    @Column(nullable = false)
    private Long version;

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

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
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
