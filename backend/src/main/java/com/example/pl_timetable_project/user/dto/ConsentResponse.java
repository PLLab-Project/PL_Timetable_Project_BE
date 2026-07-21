package com.example.pl_timetable_project.user.dto;

import java.time.Instant;
import java.util.UUID;

public record ConsentResponse(UUID consentId, String consentVersion, boolean agreed, Instant agreedAt) {
}
