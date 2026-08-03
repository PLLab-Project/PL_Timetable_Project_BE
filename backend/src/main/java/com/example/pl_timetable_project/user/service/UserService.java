package com.example.pl_timetable_project.user.service;

import com.example.pl_timetable_project.auth.repository.LoginOtpChallengeRepository;
import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.user.UserErrorCode;
import com.example.pl_timetable_project.user.dto.AcademicProgramResponse;
import com.example.pl_timetable_project.user.dto.AcademicProgramUpdateRequest;
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
import com.example.pl_timetable_project.user.repository.StudentAcademicProgramRepository;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import com.example.pl_timetable_project.user.repository.UserAccountRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private final StudentAcademicProgramRepository academicProgramRepository;
    private final LoginOtpChallengeRepository otpChallengeRepository;

    public UserService(UserAccountRepository userRepository, StudentProfileRepository profileRepository,
                       PrivacyConsentRepository consentRepository,
                       AcademicUnitLookupRepository academicUnitRepository,
                       StudentAcademicProgramRepository academicProgramRepository,
                       LoginOtpChallengeRepository otpChallengeRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.consentRepository = consentRepository;
        this.academicUnitRepository = academicUnitRepository;
        this.academicProgramRepository = academicProgramRepository;
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
            academicUnitCode = academicUnitRepository.findCurrentByCode(request.departmentId().trim())
                    .map(AcademicUnit::code)
                    .orElseThrow(() -> new BusinessException(UserErrorCode.DEPARTMENT_NOT_FOUND));
        }
        List<AcademicProgramUpdateRequest> programs = null;
        if (request.academicPrograms() != null) {
            programs = validatePrograms(request.academicPrograms());
            String primaryCode = programs.stream()
                    .filter(program -> "PRIMARY".equals(program.role()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            UserErrorCode.INVALID_ACADEMIC_PROGRAMS))
                    .academicUnitCode();
            if (academicUnitCode != null && !academicUnitCode.equals(primaryCode)) {
                throw new BusinessException(
                        UserErrorCode.INVALID_ACADEMIC_PROGRAMS,
                        "departmentId와 PRIMARY 전공 코드가 일치해야 합니다.");
            }
            academicUnitCode = primaryCode;
        }
        String studentNumber = normalizeOptional(request.studentNumber());
        if (studentNumber != null && !studentNumber.equals(profile.studentNumber())) {
            if (profileRepository.existsByStudentNumberAndUserIdNot(studentNumber, userId)) {
                throw new BusinessException(UserErrorCode.STUDENT_NUMBER_DUPLICATE);
            }
            profile.updateStudentNumber(studentNumber);
        }
        String studentType = uppercaseOptional(request.studentType());
        if (studentType == null && profile.studentType() == null) {
            studentType = "DOMESTIC";
        }
        String programPath = programs == null
                ? uppercaseOptional(request.programPath())
                : deriveProgramPath(programs);
        if (programPath == null
                && profile.programPath() == null
                && (academicUnitCode != null || profile.academicUnitCode() != null)) {
            programPath = "ADVANCED_MAJOR";
        }
        profile.update(
                request.grade(),
                academicUnitCode,
                request.admissionYear(),
                studentType,
                programPath,
                request.tutorialCompleted());
        if (programs != null) {
            academicProgramRepository.replaceActivePrograms(userId, programs);
        } else if (academicUnitCode != null) {
            academicProgramRepository.replacePrimary(userId, academicUnitCode);
        }
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
        List<AcademicProgramResponse> programs =
                academicProgramRepository.findActiveByUserId(profile.userId());
        String programPath = programs.isEmpty()
                ? profile.programPath()
                : deriveProgramPathFromResponses(programs);
        boolean graduationProfileCompleted = profile.admissionYear() != null
                && profile.academicUnitCode() != null
                && profile.studentType() != null
                && programPath != null;
        return new UserInfoResponse(user.id(), profile.studentNumber(), user.displayName(), profile.grade(),
                academicUnit == null ? null : academicUnit.code(),
                academicUnit == null ? null : academicUnit.name(),
                profile.admissionYear(),
                profile.studentType(),
                programPath,
                profile.profileCompleted(),
                graduationProfileCompleted,
                profile.tutorialCompleted(),
                profile.schoolVerified(),
                profile.schoolVerifiedAt(),
                user.createdAt(),
                programs);
    }

    private List<AcademicProgramUpdateRequest> validatePrograms(
            List<AcademicProgramUpdateRequest> requestedPrograms) {
        if (requestedPrograms.isEmpty()) {
            throw new BusinessException(UserErrorCode.INVALID_ACADEMIC_PROGRAMS);
        }
        List<AcademicProgramUpdateRequest> programs = requestedPrograms.stream()
                .map(this::normalizeProgram)
                .toList();
        long primaryCount = programs.stream()
                .filter(program -> "PRIMARY".equals(program.role()))
                .count();
        if (primaryCount != 1) {
            throw new BusinessException(
                    UserErrorCode.INVALID_ACADEMIC_PROGRAMS,
                    "PRIMARY 전공은 정확히 하나여야 합니다.");
        }
        HashSet<String> academicUnitCodes = new HashSet<>();
        for (AcademicProgramUpdateRequest program : programs) {
            if (!academicUnitCodes.add(program.academicUnitCode())) {
                throw new BusinessException(
                        UserErrorCode.INVALID_ACADEMIC_PROGRAMS,
                        "같은 학과를 여러 전공 역할로 중복 등록할 수 없습니다.");
            }
            academicUnitRepository.findCurrentByCode(program.academicUnitCode())
                    .orElseThrow(() -> new BusinessException(
                            UserErrorCode.DEPARTMENT_NOT_FOUND));
        }
        return programs.stream()
                .sorted((left, right) -> Boolean.compare(
                        !"PRIMARY".equals(left.role()),
                        !"PRIMARY".equals(right.role())))
                .toList();
    }

    private AcademicProgramUpdateRequest normalizeProgram(
            AcademicProgramUpdateRequest program) {
        if (program == null
                || normalizeOptional(program.academicUnitCode()) == null
                || normalizeOptional(program.role()) == null) {
            throw new BusinessException(UserErrorCode.INVALID_ACADEMIC_PROGRAMS);
        }
        String role = program.role().trim().toUpperCase(Locale.ROOT);
        if (!List.of("PRIMARY", "DOUBLE_MAJOR", "MINOR", "MICRO_MAJOR")
                .contains(role)) {
            throw new BusinessException(UserErrorCode.INVALID_ACADEMIC_PROGRAMS);
        }
        return new AcademicProgramUpdateRequest(program.academicUnitCode().trim(), role);
    }

    private String deriveProgramPath(List<AcademicProgramUpdateRequest> programs) {
        List<String> roles = programs.stream()
                .map(AcademicProgramUpdateRequest::role)
                .toList();
        return deriveProgramPath(roles);
    }

    private String deriveProgramPathFromResponses(List<AcademicProgramResponse> programs) {
        List<String> roles = programs.stream()
                .map(AcademicProgramResponse::role)
                .toList();
        return deriveProgramPath(roles);
    }

    private String deriveProgramPath(java.util.Collection<String> roles) {
        if (roles.contains("DOUBLE_MAJOR")) {
            return "DOUBLE_MAJOR";
        }
        if (roles.contains("MINOR")) {
            return "MINOR";
        }
        if (roles.contains("MICRO_MAJOR")) {
            return "MICRO_MAJOR";
        }
        return "ADVANCED_MAJOR";
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String uppercaseOptional(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private ConsentResponse toConsentResponse(PrivacyConsent consent) {
        return new ConsentResponse(consent.id(), consent.consentVersion(), consent.agreed(), consent.agreedAt());
    }
}
