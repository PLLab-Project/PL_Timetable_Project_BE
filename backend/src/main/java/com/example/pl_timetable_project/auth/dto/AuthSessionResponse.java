package com.example.pl_timetable_project.auth.dto;

import java.time.Instant;

public record AuthSessionResponse(
        boolean authenticated,
        AuthUserResponse user,
        Instant expiresAt
) {
}
