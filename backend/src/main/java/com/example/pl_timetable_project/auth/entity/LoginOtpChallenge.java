package com.example.pl_timetable_project.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 원본 OTP 대신 BCrypt 해시만 저장하는 일회성 인증 기록입니다. */
@Entity
@Table(name = "login_otp_challenges")
public class LoginOtpChallenge {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "student_number", nullable = false, length = 20)
    private String studentNumber;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_available_at", nullable = false)
    private Instant resendAvailableAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected LoginOtpChallenge() {
        // JPA가 엔티티를 생성할 때 사용하는 생성자입니다.
    }

    public LoginOtpChallenge(
            String studentNumber,
            String email,
            String codeHash,
            Instant expiresAt,
            Instant resendAvailableAt
    ) {
        this.studentNumber = studentNumber;
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.resendAvailableAt = resendAvailableAt;
    }

    public boolean canResendAt(Instant now) {
        return !resendAvailableAt.isAfter(now);
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public void recordFailure() {
        failedAttempts += 1;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }

    public String studentNumber() {
        return studentNumber;
    }

    public String email() {
        return email;
    }

    public String codeHash() {
        return codeHash;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant resendAvailableAt() {
        return resendAvailableAt;
    }
}
