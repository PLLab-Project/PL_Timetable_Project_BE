package com.example.pl_timetable_project.auth.service;

import com.example.pl_timetable_project.auth.AuthErrorCode;
import com.example.pl_timetable_project.auth.dto.AuthUserResponse;
import com.example.pl_timetable_project.auth.entity.SocialIdentity;
import com.example.pl_timetable_project.auth.repository.SocialIdentityRepository;
import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.user.entity.StudentProfile;
import com.example.pl_timetable_project.user.entity.UserAccount;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import com.example.pl_timetable_project.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Google OIDC subject를 로컬 계정과 연결하고 애플리케이션 세션 주체를 만듭니다. */
@Service
public class GoogleAuthenticationService {

    static final String PROVIDER = "GOOGLE";

    private final SocialIdentityRepository identityRepository;
    private final UserAccountRepository userRepository;
    private final StudentProfileRepository profileRepository;

    public GoogleAuthenticationService(
            SocialIdentityRepository identityRepository,
            UserAccountRepository userRepository,
            StudentProfileRepository profileRepository
    ) {
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @Transactional
    public LoginResult login(
            String providerSubject,
            String email,
            boolean emailVerified,
            String displayName
    ) {
        if (!emailVerified || email == null || email.isBlank()) {
            throw new BusinessException(AuthErrorCode.GOOGLE_EMAIL_NOT_VERIFIED);
        }

        SocialIdentity identity = identityRepository
                .findByProviderAndProviderSubject(PROVIDER, providerSubject)
                .orElse(null);
        UserAccount user;
        boolean newUser = false;

        if (identity == null) {
            user = userRepository.findByPrimaryEmailIgnoreCase(email).orElse(null);
            if (user == null) {
                user = userRepository.save(new UserAccount(email));
                newUser = true;
            }
            identity = identityRepository.save(
                    new SocialIdentity(user.id(), PROVIDER, providerSubject, email));
        } else {
            user = userRepository.findById(identity.userId())
                    .orElseThrow(() -> new BusinessException(AuthErrorCode.GOOGLE_LOGIN_FAILED));
            identity.updateEmail(email);
        }

        if (!"ACTIVE".equals(user.status())) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        if (displayName != null && !displayName.isBlank()) {
            user.updateProfile(displayName);
        }

        var userId = user.id();
        StudentProfile profile = profileRepository.findById(userId)
                .orElseGet(() -> profileRepository.save(new StudentProfile(userId, null)));
        return new LoginResult(
                new AuthUserResponse(user.id(), profile.studentNumber(), user.displayName()),
                newUser
        );
    }

    public record LoginResult(AuthUserResponse user, boolean newUser) {
    }
}
