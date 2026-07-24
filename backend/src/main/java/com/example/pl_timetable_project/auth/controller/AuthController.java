package com.example.pl_timetable_project.auth.controller;

import com.example.pl_timetable_project.auth.dto.AuthSessionResponse;
import com.example.pl_timetable_project.auth.dto.LogoutResponse;
import com.example.pl_timetable_project.auth.dto.OtpStartRequest;
import com.example.pl_timetable_project.auth.dto.OtpStartResponse;
import com.example.pl_timetable_project.auth.dto.OtpVerifyRequest;
import com.example.pl_timetable_project.auth.dto.OtpVerifyResponse;
import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import com.example.pl_timetable_project.auth.service.OtpAuthenticationService;
import com.example.pl_timetable_project.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 노션 인증 API 명세의 OTP·세션·로그아웃 엔드포인트입니다. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final OtpAuthenticationService authenticationService;

    public AuthController(OtpAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/otp/request")
    public ApiResponse<OtpStartResponse> requestOtp(@Valid @RequestBody OtpStartRequest request) {
        return ApiResponse.success(authenticationService.start(request.studentNumber()));
    }

    @PostMapping("/otp/verify")
    public ApiResponse<OtpVerifyResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request,
                                                     HttpServletRequest servletRequest) {
        OtpAuthenticationService.VerificationResult result =
                authenticationService.verify(request.studentNumber(), request.code());

        AuthenticatedUser principal = new AuthenticatedUser(result.user().id(), result.user().studentNumber());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Spring Security가 다음 요청에서도 로그인 사용자를 찾도록 세션에 보안 컨텍스트를 보관합니다.
        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        Instant expiresAt = Instant.now().plus(session.getMaxInactiveInterval(), ChronoUnit.SECONDS);

        return ApiResponse.success(new OtpVerifyResponse(true, result.user(), result.newUser(), expiresAt));
    }

    @GetMapping("/session")
    public ApiResponse<AuthSessionResponse> session(Authentication authentication, HttpSession session) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(new AuthSessionResponse(
                true,
                authenticationService.getSessionUser(user.userId()),
                Instant.now().plus(session.getMaxInactiveInterval(), ChronoUnit.SECONDS)
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.success(new LogoutResponse("로그아웃되었습니다."));
    }
}
