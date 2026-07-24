package com.example.pl_timetable_project.auth.repository;

import com.example.pl_timetable_project.auth.entity.LoginOtpChallenge;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 학번별 가장 최근의 미사용 OTP를 조회합니다. */
public interface LoginOtpChallengeRepository extends JpaRepository<LoginOtpChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginOtpChallenge> findFirstByStudentNumberAndConsumedAtIsNullOrderByCreatedAtDesc(
            String studentNumber
    );

    /** 회원 탈퇴 시 학번에 연결된 사용·미사용 OTP 기록을 모두 삭제합니다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LoginOtpChallenge challenge where challenge.studentNumber = :studentNumber")
    int deleteAllByStudentNumber(@Param("studentNumber") String studentNumber);
}
