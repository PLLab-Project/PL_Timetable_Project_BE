package com.example.pl_timetable_project.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.pl_timetable_project.auth.AuthErrorCode;
import com.example.pl_timetable_project.auth.entity.SocialIdentity;
import com.example.pl_timetable_project.auth.repository.SocialIdentityRepository;
import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.user.entity.StudentProfile;
import com.example.pl_timetable_project.user.entity.UserAccount;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import com.example.pl_timetable_project.user.repository.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GoogleAuthenticationServiceTest {

    @Mock
    private SocialIdentityRepository identityRepository;
    @Mock
    private UserAccountRepository userRepository;
    @Mock
    private StudentProfileRepository profileRepository;

    private GoogleAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAuthenticationService(
                identityRepository, userRepository, profileRepository);
    }

    @Test
    void linksVerifiedGoogleIdentityToExistingEmailAccount() {
        UUID userId = UUID.randomUUID();
        UserAccount user = new UserAccount("student@daejin.ac.kr");
        ReflectionTestUtils.setField(user, "id", userId);
        StudentProfile profile = new StudentProfile(userId, "20201234");

        when(identityRepository.findByProviderAndProviderSubject("GOOGLE", "google-sub"))
                .thenReturn(Optional.empty());
        when(userRepository.findByPrimaryEmailIgnoreCase("student@daejin.ac.kr"))
                .thenReturn(Optional.of(user));
        when(identityRepository.save(any(SocialIdentity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.findById(userId)).thenReturn(Optional.of(profile));

        GoogleAuthenticationService.LoginResult result = service.login(
                "google-sub", "student@daejin.ac.kr", true, "홍길동");

        assertThat(result.newUser()).isFalse();
        assertThat(result.user().id()).isEqualTo(userId);
        assertThat(result.user().studentNumber()).isEqualTo("20201234");
        assertThat(result.user().name()).isEqualTo("홍길동");

        ArgumentCaptor<SocialIdentity> identityCaptor =
                ArgumentCaptor.forClass(SocialIdentity.class);
        verify(identityRepository).save(identityCaptor.capture());
        SocialIdentity identity = identityCaptor.getValue();
        assertThat(identity.provider()).isEqualTo("GOOGLE");
        assertThat(identity.providerSubject()).isEqualTo("google-sub");
        assertThat(identity.email()).isEqualTo("student@daejin.ac.kr");
    }

    @Test
    void rejectsUnverifiedGoogleEmail() {
        assertThatThrownBy(() -> service.login(
                "google-sub", "student@example.com", false, "홍길동"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(AuthErrorCode.GOOGLE_EMAIL_NOT_VERIFIED));
    }
}
