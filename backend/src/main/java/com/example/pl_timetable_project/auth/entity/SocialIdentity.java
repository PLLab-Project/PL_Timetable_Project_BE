package com.example.pl_timetable_project.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 외부 로그인 공급자의 변경 가능한 이메일과 불변 subject를 로컬 사용자에 연결합니다. */
@Entity
@Table(name = "social_identities")
public class SocialIdentity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(length = 320)
    private String email;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    protected SocialIdentity() {
        // JPA 전용 생성자입니다.
    }

    public SocialIdentity(UUID userId, String provider, String providerSubject, String email) {
        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.email = email;
    }

    public void updateEmail(String email) {
        this.email = email;
        this.updatedAt = Instant.now();
    }

    public UUID userId() {
        return userId;
    }

    public String provider() {
        return provider;
    }

    public String providerSubject() {
        return providerSubject;
    }

    public String email() {
        return email;
    }
}
