package com.example.pl_timetable_project.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ConsentCreateRequest(@NotBlank String consentVersion, boolean agreed) {
}
