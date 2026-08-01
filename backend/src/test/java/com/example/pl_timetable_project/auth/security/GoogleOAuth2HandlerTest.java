package com.example.pl_timetable_project.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.pl_timetable_project.auth.AuthErrorCode;
import com.example.pl_timetable_project.auth.config.GoogleAuthProperties;
import com.example.pl_timetable_project.auth.dto.AuthUserResponse;
import com.example.pl_timetable_project.auth.service.GoogleAuthenticationService;
import com.example.pl_timetable_project.common.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2HandlerTest {

    private static final String SUCCESS_REDIRECT =
            "https://pl-timetable-project-fe.vercel.app/?auth=google-success";
    private static final String FAILURE_REDIRECT =
            "https://pl-timetable-project-fe.vercel.app/?auth=google-failure";

    @Mock
    private GoogleAuthenticationService authenticationService;
    @Mock
    private ObjectProvider<OAuth2AuthorizedClientRepository> repositoryProvider;
    @Mock
    private OAuth2AuthorizedClientRepository authorizedClientRepository;
    @Mock
    private Authentication oauthAuthentication;
    @Mock
    private OidcUser oidcUser;

    private GoogleOAuth2SuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        GoogleAuthProperties properties =
                new GoogleAuthProperties(true, SUCCESS_REDIRECT, FAILURE_REDIRECT);
        successHandler = new GoogleOAuth2SuccessHandler(
                authenticationService, repositoryProvider, properties);
    }

    @Test
    void createsLocalSessionAndReturnsBrowserToFrontend() throws Exception {
        UUID userId = UUID.randomUUID();
        configureGooglePrincipal();
        when(authenticationService.login(
                "google-sub", "student@daejin.ac.kr", true, "홍길동"))
                .thenReturn(new GoogleAuthenticationService.LoginResult(
                        new AuthUserResponse(userId, "20201234", "홍길동", true), false));
        when(repositoryProvider.getIfAvailable()).thenReturn(authorizedClientRepository);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, oauthAuthentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_REDIRECT);
        assertThat(request.getSession(false)).isNotNull();
        SecurityContext context = (SecurityContext) request.getSession(false).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).isNotNull();
        assertThat(context.getAuthentication().isAuthenticated()).isTrue();
        assertThat(context.getAuthentication().getPrincipal())
                .isEqualTo(new AuthenticatedUser(userId, "20201234"));
        verify(authorizedClientRepository).removeAuthorizedClient(
                "google", oauthAuthentication, request, response);
    }

    @Test
    void usesStableUuidAsSpringSessionPrincipalName() {
        UUID userId = UUID.randomUUID();

        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                "20261234",
                true);
        Authentication sessionAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        java.util.List.of());

        assertThat(principal.getName()).isEqualTo(userId.toString());
        assertThat(principal.getName()).hasSize(36);
        assertThat(sessionAuthentication.getName()).isEqualTo(userId.toString());
    }

    @Test
    void returnsBrowserToFrontendFailurePageWhenAccountLinkingFails() throws Exception {
        configureGooglePrincipal();
        when(authenticationService.login(
                "google-sub", "student@daejin.ac.kr", true, "홍길동"))
                .thenThrow(new BusinessException(AuthErrorCode.ACCOUNT_DISABLED));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, oauthAuthentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(FAILURE_REDIRECT);
        assertThat(request.getSession(false)).isNull();
    }

    private void configureGooglePrincipal() {
        when(oauthAuthentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getSubject()).thenReturn("google-sub");
        when(oidcUser.getEmail()).thenReturn("student@daejin.ac.kr");
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(oidcUser.getFullName()).thenReturn("홍길동");
    }
}
