package com.example.pl_timetable_project.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 사용자의 학번·학과·학년 등 학사 프로필입니다. */
@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "student_number", length = 20, unique = true)
    private String studentNumber;

    @Column(name = "academic_unit_code", length = 40)
    private String academicUnitCode;

    private Short grade;

    @Column(name = "admission_year")
    private Integer admissionYear;

    @Column(name = "entry_type", length = 24)
    private String entryType;

    @Column(name = "student_type", length = 24)
    private String studentType;

    @Column(name = "section_group", length = 24)
    private String sectionGroup;

    @Column(name = "program_path", length = 32)
    private String programPath;

    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected StudentProfile() {
        // JPA 전용 생성자입니다.
    }

    public StudentProfile(UUID userId, String studentNumber) {
        this.userId = userId;
        this.studentNumber = studentNumber;
    }

    public void update(Short grade, String academicUnitCode) {
        if (grade != null) {
            this.grade = grade;
        }
        if (academicUnitCode != null) {
            this.academicUnitCode = academicUnitCode;
        }
        this.profileCompleted = this.grade != null && this.academicUnitCode != null;
        this.updatedAt = Instant.now();
    }

    public UUID userId() {
        return userId;
    }

    public String studentNumber() {
        return studentNumber;
    }

    public String academicUnitCode() {
        return academicUnitCode;
    }

    public Short grade() {
        return grade;
    }
}
