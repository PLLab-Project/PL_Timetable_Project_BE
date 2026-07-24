package com.example.pl_timetable_project.user.service;

import com.example.pl_timetable_project.auth.repository.LoginOtpChallengeRepository;
import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.user.UserErrorCode;
import com.example.pl_timetable_project.user.dto.ConsentCreateRequest;
import com.example.pl_timetable_project.user.dto.ConsentResponse;
import com.example.pl_timetable_project.user.dto.UserDeleteResponse;
import com.example.pl_timetable_project.user.dto.UserInfoResponse;
import com.example.pl_timetable_project.user.dto.UserUpdateRequest;
import com.example.pl_timetable_project.user.entity.PrivacyConsent;
import com.example.pl_timetable_project.user.entity.StudentProfile;
import com.example.pl_timetable_project.user.entity.UserAccount;
import com.example.pl_timetable_project.user.repository.AcademicUnitLookupRepository;
import com.example.pl_timetable_project.user.repository.AcademicUnitLookupRepository.AcademicUnit;
import com.example.pl_timetable_project.user.repository.PrivacyConsentRepository;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import com.example.pl_timetable_project.user.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 정보 조회·수정, 개인정보 동의, 탈퇴를 처리합니다. */
@Service
public class UserService {
    private final UserAccountRepository userRepository;
    private final StudentProfileRepository profileRepository;
    private final PrivacyConsentRepository consentRepository;
    private final AcademicUnitLookupRepository academicUnitRepository;
    private final LoginOtpChallengeRepository otpChallengeRepository;

    public UserService(UserAccountRepository userRepository, StudentProfileRepository profileRepository,
                       PrivacyConsentRepository consentRepository,
                       AcademicUnitLookupRepository academicUnitRepository,
                       LoginOtpChallengeRepository otpChallengeRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.consentRepository = consentRepository;
        this.academicUnitRepository = academicUnitRepository;
        this.otpChallengeRepository = otpChallengeRepository;
    }

    @Transactional(readOnly = true)
    public UserInfoResponse get(UUID userId) {
        return toResponse(findUser(userId), findProfile(userId));
    }

    @Transactional
    public UserInfoResponse update(UUID userId, UserUpdateRequest request) {
        UserAccount user = findUser(userId);
        StudentProfile profile = findProfile(userId);
        if (request.name() != null) {
            user.updateProfile(request.name().trim());
        }
        String academicUnitCode = null;
        if (request.departmentId() != null) {
            academicUnitCode = academicUnitRepository.findCurrentByCode(request.departmentId())
                    .map(AcademicUnit::code)
                    .orElseThrow(() -> new BusinessException(UserErrorCode.DEPARTMENT_NOT_FOUND));
        }
        profile.update(request.grade(), academicUnitCode);
        return toResponse(user, profile);
    }

    @Transactional
    public ConsentResponse saveConsent(UUID userId, ConsentCreateRequest request) {
        findUser(userId);
        PrivacyConsent consent = consentRepository
                .findByUserIdAndConsentTypeAndConsentVersion(userId, "PRIVACY_POLICY", request.consentVersion())
                .orElseGet(() -> consentRepository.save(
                        new PrivacyConsent(userId, request.consentVersion(), request.agreed())));
        return toConsentResponse(consent);
    }

    @Transactional(readOnly = true)
    public List<ConsentResponse> getConsents(UUID userId) {
        findUser(userId);
        return consentRepository.findAllByUserIdOrderByAgreedAtDesc(userId).stream()
                .map(this::toConsentResponse).toList();
    }

    @Transactional
    public UserDeleteResponse withdraw(UUID userId, boolean confirmed) {
        if (!confirmed) {
            throw new BusinessException(UserErrorCode.CONFIRMATION_REQUIRED);
        }

        UserAccount user = findUser(userId);
        StudentProfile profile = findProfile(userId);

        // OTP 테이블에는 users 외래 키가 없으므로 학번에 연결된 인증 기록을 직접 삭제합니다.
        otpChallengeRepository.deleteAllByStudentNumber(profile.studentNumber());

        /*
         * users 행을 삭제하면 DB의 ON DELETE CASCADE 설정에 따라 학생 프로필, 개인정보 동의,
         * 소셜 계정, 리뷰, 이수과목, 시간표와 자동 편성 작업 및 결과가 한 트랜잭션에서 삭제됩니다.
         * flush로 실제 DELETE와 외래 키 연쇄 삭제가 성공했는지 응답 전에 확인합니다.
         */
        userRepository.delete(user);
        userRepository.flush();

        Instant deletedAt = Instant.now();
        return new UserDeleteResponse("회원 탈퇴가 완료되었고 사용자 데이터가 모두 삭제되었습니다.", deletedAt);
    }

    private UserAccount findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private StudentProfile findProfile(UUID userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private UserInfoResponse toResponse(UserAccount user, StudentProfile profile) {
        AcademicUnit academicUnit = profile.academicUnitCode() == null
                ? null
                : academicUnitRepository.findByCode(profile.academicUnitCode()).orElse(null);
        return new UserInfoResponse(user.id(), profile.studentNumber(), user.displayName(), profile.grade(),
                academicUnit == null ? null : academicUnit.code(),
                academicUnit == null ? null : academicUnit.name(),
                user.createdAt());
    }

    private ConsentResponse toConsentResponse(PrivacyConsent consent) {
        return new ConsentResponse(consent.id(), consent.consentVersion(), consent.agreed(), consent.agreedAt());
    }
}
