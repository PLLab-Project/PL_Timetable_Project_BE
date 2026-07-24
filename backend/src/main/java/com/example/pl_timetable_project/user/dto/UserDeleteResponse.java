package com.example.pl_timetable_project.user.dto;

import java.time.Instant;

public record UserDeleteResponse(String message, Instant deletedAt) {
}
