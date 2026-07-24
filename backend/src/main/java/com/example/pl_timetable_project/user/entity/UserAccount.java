package com.example.pl_timetable_project.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 로그인 계정의 공통 정보입니다. 학사 정보는 StudentProfile로 분리합니다. */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(name = "primary_email", length = 320)
    private String primaryEmail;

    @Column(nullable = false, length = 24)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected UserAccount() {
        // JPA 전용 생성자입니다.
    }

    public UserAccount(String primaryEmail) {
        this.primaryEmail = primaryEmail;
    }

    public void updateProfile(String displayName) {
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public void withdraw() {
        this.status = "WITHDRAWN";
        this.updatedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String primaryEmail() {
        return primaryEmail;
    }

    public String status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
