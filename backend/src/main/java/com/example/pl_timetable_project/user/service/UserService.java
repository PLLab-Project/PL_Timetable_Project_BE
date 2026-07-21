package com.example.pl_timetable_project.user.service;

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

    public UserService(UserAccountRepository userRepository, StudentProfileRepository profileRepository,
                       PrivacyConsentRepository consentRepository,
                       AcademicUnitLookupRepository academicUnitRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.consentRepository = consentRepository;
        this.academicUnitRepository = academicUnitRepository;
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
        String departmentName = null;
        if (request.departmentId() != null) {
            departmentName = academicUnitRepository.findCurrentNameByCode(request.departmentId())
                    .orElseThrow(() -> new BusinessException(UserErrorCode.DEPARTMENT_NOT_FOUND));
        }
        profile.update(request.grade(), request.departmentId(), departmentName);
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
        findUser(userId).withdraw(); // 연관 데이터 보존을 위해 물리 삭제 대신 탈퇴 상태로 바꿉니다.
        Instant deletedAt = Instant.now();
        return new UserDeleteResponse("회원 탈퇴가 완료되었습니다.", deletedAt);
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
        return new UserInfoResponse(user.id(), profile.studentNumber(), user.displayName(), profile.grade(),
                profile.academicUnitKey(), profile.academicUnitName(), user.createdAt());
    }

    private ConsentResponse toConsentResponse(PrivacyConsent consent) {
        return new ConsentResponse(consent.id(), consent.consentVersion(), consent.agreed(), consent.agreedAt());
    }
}
