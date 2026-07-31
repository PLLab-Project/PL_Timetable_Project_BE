package com.example.pl_timetable_project.auth.service;

import com.example.pl_timetable_project.auth.AuthErrorCode;
import com.example.pl_timetable_project.auth.config.AuthProperties;
import com.example.pl_timetable_project.auth.dto.AuthUserResponse;
import com.example.pl_timetable_project.auth.dto.OtpStartResponse;
import com.example.pl_timetable_project.auth.dto.SchoolVerificationResponse;
import com.example.pl_timetable_project.auth.entity.LoginOtpChallenge;
import com.example.pl_timetable_project.auth.repository.LoginOtpChallengeRepository;
import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.user.entity.StudentProfile;
import com.example.pl_timetable_project.user.entity.UserAccount;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import com.example.pl_timetable_project.user.repository.UserAccountRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OTP 생성부터 사용자 계정 연결까지 담당하는 인증 서비스입니다. */
@Service
public class OtpAuthenticationService {

    private final LoginOtpChallengeRepository challengeRepository;
    private final UserAccountRepository userRepository;
    private final StudentProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpSender otpSender;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpAuthenticationService(LoginOtpChallengeRepository challengeRepository,
                                    UserAccountRepository userRepository,
                                    StudentProfileRepository profileRepository,
                                    PasswordEncoder passwordEncoder,
                                    OtpSender otpSender,
                                    AuthProperties properties) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpSender = otpSender;
        this.properties = properties;
    }

    /** 학번으로 학교 이메일을 만들고 6자리 OTP를 전송합니다. */
    @Transactional
    public OtpStartResponse start(String studentNumber) {
        return createChallenge(studentNumber);
    }

    /** Google 로그인 사용자가 입력한 학번을 다른 계정이 사용 중인지 확인하고 OTP를 전송합니다. */
    @Transactional
    public OtpStartResponse startSchoolVerification(UUID userId, String studentNumber) {
        UserAccount user = requireActiveUser(userId);
        StudentProfile profile = requireProfile(userId);
        if (profile.schoolVerified() && studentNumber.equals(profile.studentNumber())) {
            throw new BusinessException(AuthErrorCode.SCHOOL_ALREADY_VERIFIED);
        }
        profileRepository.findByStudentNumber(studentNumber)
                .filter(existing -> !existing.userId().equals(userId))
                .ifPresent(existing -> {
                    throw new BusinessException(AuthErrorCode.STUDENT_NUMBER_ALREADY_VERIFIED);
                });
        if (!"ACTIVE".equals(user.status())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        return createChallenge(studentNumber);
    }

    private OtpStartResponse createChallenge(String studentNumber) {
        Instant now = Instant.now();
        challengeRepository.findFirstByStudentNumberAndConsumedAtIsNullOrderByCreatedAtDesc(studentNumber)
                .ifPresent(previous -> {
                    if (!previous.canResendAt(now)) {
                        throw new BusinessException(AuthErrorCode.TOO_MANY_REQUESTS);
                    }
                    previous.consume(now);
                });

        String email = studentNumber + "@" + properties.schoolEmailDomain();
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        LoginOtpChallenge challenge = new LoginOtpChallenge(
                studentNumber,
                email,
                passwordEncoder.encode(code), // DB 유출 시에도 인증번호 원문이 보이지 않게 해시로 저장합니다.
                now.plusSeconds(properties.otp().expirationSeconds()),
                now.plusSeconds(properties.otp().cooldownSeconds())
        );
        challengeRepository.save(challenge);
        otpSender.send(email, code);

        return new OtpStartResponse(
                "학교 이메일로 인증번호를 전송했습니다.",
                properties.otp().cooldownSeconds(),
                properties.otp().expirationSeconds()
        );
    }

    /** OTP를 검증하고, 첫 로그인이라면 사용자와 학생 프로필을 함께 생성합니다. */
    // 실패 횟수와 만료 처리는 예외 응답이 나가도 DB에 남아야 무차별 대입을 막을 수 있습니다.
    @Transactional(noRollbackFor = BusinessException.class)
    public VerificationResult verify(String studentNumber, String code) {
        Instant now = Instant.now();
        LoginOtpChallenge challenge = verifyChallenge(studentNumber, code, now);
        StudentProfile profile = profileRepository.findByStudentNumber(studentNumber)
                .orElse(null);
        UserAccount user;
        boolean newUser = false;
        if (profile != null) {
            user = userRepository.findById(profile.userId())
                    .orElseThrow(() -> new BusinessException(AuthErrorCode.SESSION_EXPIRED));
        } else {
            user = userRepository.findByPrimaryEmailIgnoreCase(challenge.email()).orElse(null);
            if (user == null) {
                user = userRepository.save(new UserAccount(challenge.email()));
                profile = profileRepository.save(new StudentProfile(user.id(), studentNumber));
                newUser = true;
            } else {
                profile = profileRepository.findById(user.id()).orElse(null);
                if (profile == null) {
                    profile = profileRepository.save(
                            new StudentProfile(user.id(), studentNumber));
                }
                if (profile.studentNumber() == null) {
                    profile.verifySchoolIdentity(studentNumber, now);
                } else if (!profile.studentNumber().equals(studentNumber)) {
                    throw new BusinessException(AuthErrorCode.INVALID_OR_EXPIRED_CODE);
                }
            }
        }
        if (!profile.schoolVerified()) {
            profile.verifySchoolIdentity(studentNumber, now);
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        }

        return new VerificationResult(
                toAuthUser(user, profile),
                newUser
        );
    }

    /** 현재 Google 세션의 사용자에게 OTP로 확인한 학교 학번을 연결합니다. */
    @Transactional(noRollbackFor = BusinessException.class)
    public SchoolVerificationResponse verifySchoolVerification(
            UUID userId,
            String studentNumber,
            String code
    ) {
        Instant now = Instant.now();
        UserAccount user = requireActiveUser(userId);
        StudentProfile profile = requireProfile(userId);
        profileRepository.findByStudentNumber(studentNumber)
                .filter(existing -> !existing.userId().equals(userId))
                .ifPresent(existing -> {
                    throw new BusinessException(AuthErrorCode.STUDENT_NUMBER_ALREADY_VERIFIED);
                });

        String previousStudentNumber = profile.studentNumber();
        verifyChallenge(studentNumber, code, now);
        profile.verifySchoolIdentity(studentNumber, now);
        if (previousStudentNumber != null
                && !previousStudentNumber.equals(studentNumber)) {
            // 학번 변경 뒤 탈퇴 시 예전 학번의 OTP 기록이 남지 않도록 즉시 정리합니다.
            challengeRepository.deleteAllByStudentNumber(previousStudentNumber);
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        return new SchoolVerificationResponse(true, studentNumber, now);
    }

    private LoginOtpChallenge verifyChallenge(
            String studentNumber,
            String code,
            Instant now
    ) {
        LoginOtpChallenge challenge = challengeRepository
                .findFirstByStudentNumberAndConsumedAtIsNullOrderByCreatedAtDesc(studentNumber)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_OR_EXPIRED_CODE));

        if (challenge.isExpiredAt(now)) {
            challenge.consume(now);
            throw new BusinessException(AuthErrorCode.INVALID_OR_EXPIRED_CODE);
        }
        if (challenge.failedAttempts() >= properties.otp().maxAttempts()) {
            throw new BusinessException(AuthErrorCode.TOO_MANY_ATTEMPTS);
        }
        if (!passwordEncoder.matches(code, challenge.codeHash())) {
            challenge.recordFailure();
            if (challenge.failedAttempts() >= properties.otp().maxAttempts()) {
                challenge.consume(now);
                throw new BusinessException(AuthErrorCode.TOO_MANY_ATTEMPTS);
            }
            throw new BusinessException(AuthErrorCode.INVALID_OR_EXPIRED_CODE);
        }

        challenge.consume(now);
        return challenge;
    }

    public record VerificationResult(AuthUserResponse user, boolean newUser) {
    }

    /** 세션 확인 응답에 표시할 최신 사용자 정보를 DB에서 읽습니다. */
    @Transactional(readOnly = true)
    public AuthUserResponse getSessionUser(java.util.UUID userId) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.SESSION_EXPIRED));
        StudentProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.SESSION_EXPIRED));
        return toAuthUser(user, profile);
    }

    private UserAccount requireActiveUser(UUID userId) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.SESSION_EXPIRED));
        if (!"ACTIVE".equals(user.status())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    private StudentProfile requireProfile(UUID userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.SESSION_EXPIRED));
    }

    private AuthUserResponse toAuthUser(UserAccount user, StudentProfile profile) {
        return new AuthUserResponse(
                user.id(),
                profile.studentNumber(),
                user.displayName(),
                profile.schoolVerified());
    }
}
