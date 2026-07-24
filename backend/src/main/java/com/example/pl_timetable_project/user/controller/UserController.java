package com.example.pl_timetable_project.user.controller;

import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import com.example.pl_timetable_project.common.response.ApiResponse;
import com.example.pl_timetable_project.user.dto.ConsentCreateRequest;
import com.example.pl_timetable_project.user.dto.ConsentResponse;
import com.example.pl_timetable_project.user.dto.UserDeleteRequest;
import com.example.pl_timetable_project.user.dto.UserDeleteResponse;
import com.example.pl_timetable_project.user.dto.UserInfoResponse;
import com.example.pl_timetable_project.user.dto.UserUpdateRequest;
import com.example.pl_timetable_project.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 본인만 접근할 수 있는 회원 API입니다. */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<UserInfoResponse> getMe(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(userService.get(principal.userId()));
    }

    @PatchMapping
    public ApiResponse<UserInfoResponse> updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.update(principal.userId(), request));
    }

    @PostMapping("/privacy-consents")
    public ApiResponse<ConsentResponse> saveConsent(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @Valid @RequestBody ConsentCreateRequest request) {
        return ApiResponse.success(userService.saveConsent(principal.userId(), request));
    }

    @GetMapping("/privacy-consents")
    public ApiResponse<List<ConsentResponse>> getConsents(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(userService.getConsents(principal.userId()));
    }

    @DeleteMapping
    public ApiResponse<UserDeleteResponse> withdraw(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @RequestBody UserDeleteRequest request,
                                                     HttpServletRequest servletRequest) {
        UserDeleteResponse response = userService.withdraw(principal.userId(), request.confirmed());
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate(); // 탈퇴 즉시 기존 로그인 세션도 종료합니다.
        }
        return ApiResponse.success(response);
    }
}
