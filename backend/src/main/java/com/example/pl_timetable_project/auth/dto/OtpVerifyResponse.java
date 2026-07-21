package com.example.pl_timetable_project.auth.dto;

import java.time.Instant;

public record OtpVerifyResponse(
        boolean authenticated,
        AuthUserResponse user,
        boolean newUser,
        Instant expiresAt
) {
}
