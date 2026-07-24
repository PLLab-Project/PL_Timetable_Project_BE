package com.example.pl_timetable_project.auth.service;

import com.example.pl_timetable_project.auth.AuthErrorCode;
import com.example.pl_timetable_project.auth.config.AuthProperties;
import com.example.pl_timetable_project.auth.dto.AuthUserResponse;
import com.example.pl_timetable_project.auth.dto.OtpStartResponse;
import com.example.pl_timetable_project.auth.entity.LoginOtpChallenge;
import com.example.pl_timetable_project.auth.repository.LoginOtpChallengeRepository;
import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.user.entity.StudentProfile;
import com.example.pl_timetable_project.user.entity.UserAccount;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import com.example.pl_timetable_project.user.repository.UserAccountRepository;
import java.security.SecureRandom;
import java.time.Instant;
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

        challenge.consume(now); // 성공한 OTP는 재사용할 수 없게 즉시 소모 처리합니다.
        UserAccount user = userRepository.findByPrimaryEmailIgnoreCase(challenge.email()).orElse(null);
        boolean newUser = user == null;
        if (newUser) {
            user = userRepository.save(new UserAccount(challenge.email()));
            profileRepository.save(new StudentProfile(user.id(), studentNumber));
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        StudentProfile profile = profileRepository.findById(user.id())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.SESSION_EXPIRED));

        return new VerificationResult(
                new AuthUserResponse(user.id(), profile.studentNumber(), user.displayName()),
                newUser
        );
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
        return new AuthUserResponse(user.id(), profile.studentNumber(), user.displayName());
    }
}
