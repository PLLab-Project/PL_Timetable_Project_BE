package com.example.pl_timetable_project.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.pl_timetable_project.auth.AuthErrorCode;
import com.example.pl_timetable_project.auth.config.AuthProperties;
import com.example.pl_timetable_project.auth.entity.LoginOtpChallenge;
import com.example.pl_timetable_project.auth.repository.LoginOtpChallengeRepository;
import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import com.example.pl_timetable_project.user.repository.UserAccountRepository;
import com.example.pl_timetable_project.user.entity.StudentProfile;
import com.example.pl_timetable_project.user.entity.UserAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class OtpAuthenticationServiceTest {

    private LoginOtpChallengeRepository challengeRepository;
    private UserAccountRepository userRepository;
    private StudentProfileRepository profileRepository;
    private PasswordEncoder passwordEncoder;
    private OtpSender otpSender;
    private OtpAuthenticationService service;

    @BeforeEach
    void setUp() {
        challengeRepository = mock(LoginOtpChallengeRepository.class);
        userRepository = mock(UserAccountRepository.class);
        profileRepository = mock(StudentProfileRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        otpSender = mock(OtpSender.class);
        when(passwordEncoder.encode(any())).thenReturn("hashed-code");
        AuthProperties properties = new AuthProperties(
                "daejin.ac.kr", new AuthProperties.Otp(300, 60, 5));
        service = new OtpAuthenticationService(challengeRepository, userRepository, profileRepository,
                passwordEncoder, otpSender, properties);
    }

    @Test
    void createsHashedChallengeAndSendsSixDigitCode() {
        when(challengeRepository
                .findFirstByStudentNumberAndConsumedAtIsNullOrderByCreatedAtDesc("20260001"))
                .thenReturn(Optional.empty());

        var response = service.start("20260001");

        ArgumentCaptor<LoginOtpChallenge> challengeCaptor =
                ArgumentCaptor.forClass(LoginOtpChallenge.class);
        verify(challengeRepository).save(challengeCaptor.capture());
        LoginOtpChallenge challenge = challengeCaptor.getValue();
        assertThat(challenge.studentNumber()).isEqualTo("20260001");
        assertThat(challenge.email()).isEqualTo("20260001@daejin.ac.kr");
        assertThat(challenge.codeHash()).isEqualTo("hashed-code");
        verify(otpSender).send(
                org.mockito.ArgumentMatchers.eq("20260001@daejin.ac.kr"),
                org.mockito.ArgumentMatchers.matches("\\d{6}"));
        assertThat(response.cooldownSeconds()).isEqualTo(60);
        assertThat(response.expiresInSeconds()).isEqualTo(300);
    }

    @Test
    void rejectsResendDuringCooldownWithoutSendingAnotherCode() {
        Instant now = Instant.now();
        LoginOtpChallenge activeChallenge = new LoginOtpChallenge(
                "20260001", "20260001@daejin.ac.kr", "hash",
                now.plusSeconds(300), now.plusSeconds(60));
        when(challengeRepository
                .findFirstByStudentNumberAndConsumedAtIsNullOrderByCreatedAtDesc("20260001"))
                .thenReturn(Optional.of(activeChallenge));

        assertThatThrownBy(() -> service.start("20260001"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(AuthErrorCode.TOO_MANY_REQUESTS));
        verify(challengeRepository, never()).save(any());
        verify(otpSender, never()).send(any(), any());
    }

    @Test
    void logsIntoExistingAccountAfterStudentNumberWasChanged() {
        Instant now = Instant.now();
        LoginOtpChallenge challenge = new LoginOtpChallenge(
                "20261234",
                "20261234@daejin.ac.kr",
                "hash",
                now.plusSeconds(300),
                now);
        UUID userId = UUID.randomUUID();
        StudentProfile profile = mock(StudentProfile.class);
        UserAccount user = mock(UserAccount.class);
        when(challengeRepository
                .findFirstByStudentNumberAndConsumedAtIsNullOrderByCreatedAtDesc("20261234"))
                .thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(profileRepository.findByStudentNumber("20261234"))
                .thenReturn(Optional.of(profile));
        when(profile.userId()).thenReturn(userId);
        when(profile.studentNumber()).thenReturn("20261234");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(user.id()).thenReturn(userId);
        when(user.status()).thenReturn("ACTIVE");
        when(user.displayName()).thenReturn("사용자");

        var result = service.verify("20261234", "123456");

        assertThat(result.newUser()).isFalse();
        assertThat(result.user().id()).isEqualTo(userId);
        assertThat(result.user().studentNumber()).isEqualTo("20261234");
        verify(userRepository, never())
                .findByPrimaryEmailIgnoreCase("20261234@daejin.ac.kr");
    }
}
