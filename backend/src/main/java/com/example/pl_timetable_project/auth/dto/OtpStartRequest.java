package com.example.pl_timetable_project.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 학교 이메일을 만들 때 사용하는 학번 입력입니다. */
public record OtpStartRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9]{6,20}$", message = "학번은 숫자 6~20자리여야 합니다.")
        String studentNumber
) {
}
