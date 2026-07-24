package com.example.pl_timetable_project.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 사용자가 동의한 개인정보 처리방침 버전을 기록합니다. */
@Entity
@Table(name = "privacy_consents")
public class PrivacyConsent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "consent_type", nullable = false, length = 40)
    private String consentType = "PRIVACY_POLICY";

    @Column(name = "consent_version", nullable = false, length = 40)
    private String consentVersion;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false, updatable = false)
    private Instant agreedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected PrivacyConsent() {
        // JPA 전용 생성자입니다.
    }

    public PrivacyConsent(UUID userId, String consentVersion, boolean agreed) {
        this.userId = userId;
        this.consentVersion = consentVersion;
        this.agreed = agreed;
        this.agreedAt = Instant.now();
        if (!agreed) {
            this.withdrawnAt = this.agreedAt;
        }
    }

    public UUID id() {
        return id;
    }

    public String consentVersion() {
        return consentVersion;
    }

    public boolean agreed() {
        return agreed;
    }

    public Instant agreedAt() {
        return agreedAt;
    }
}
