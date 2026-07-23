package com.example.pl_timetable_project.auth.repository;

import com.example.pl_timetable_project.auth.entity.LoginOtpChallenge;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

/** 학번별 가장 최근의 미사용 OTP를 조회합니다. */
public interface LoginOtpChallengeRepository extends JpaRepository<LoginOtpChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginOtpChallenge> findFirstByStudentNumberAndConsumedAtIsNullOrderByCreatedAtDesc(
            String studentNumber
    );
}
