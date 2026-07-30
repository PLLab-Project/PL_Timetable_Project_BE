package com.example.pl_timetable_project.auth.security;

import com.example.pl_timetable_project.auth.config.GoogleAuthProperties;
import com.example.pl_timetable_project.auth.service.GoogleAuthenticationService;
import com.example.pl_timetable_project.common.exception.BusinessException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

/** Google 인증 결과를 외부 토큰이 없는 최소 로컬 세션으로 교체합니다. */
@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final GoogleAuthenticationService authenticationService;
    private final ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepository;
    private final GoogleAuthProperties properties;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public GoogleOAuth2SuccessHandler(
            GoogleAuthenticationService authenticationService,
            ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepository,
            GoogleAuthProperties properties
    ) {
        this.authenticationService = authenticationService;
        this.authorizedClientRepository = authorizedClientRepository;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        GoogleAuthenticationService.LoginResult result;
        try {
            result = authenticationService.login(
                    oidcUser.getSubject(),
                    oidcUser.getEmail(),
                    Boolean.TRUE.equals(oidcUser.getEmailVerified()),
                    oidcUser.getFullName()
            );
        } catch (BusinessException exception) {
            SecurityContextHolder.clearContext();
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }
            response.sendRedirect(properties.failureRedirectUri());
            return;
        }

        AuthenticatedUser principal =
                new AuthenticatedUser(result.user().id(), result.user().studentNumber());
        Authentication localAuthentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(localAuthentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        securityContextRepository.saveContext(context, request, response);

        OAuth2AuthorizedClientRepository repository = authorizedClientRepository.getIfAvailable();
        if (repository != null) {
            repository.removeAuthorizedClient("google", authentication, request, response);
        }
        response.sendRedirect(properties.successRedirectUri());
    }
}
