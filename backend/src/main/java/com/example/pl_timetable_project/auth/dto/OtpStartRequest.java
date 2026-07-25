package com.example.pl_timetable_project.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 학교 이메일을 만들 때 사용하는 학번 입력입니다. */
@Schema(description = "학교 이메일 OTP 발송 요청")
public record OtpStartRequest(
        @Schema(
                description = "학교 이메일 주소를 결정할 숫자 학번",
                example = "20201234")
        @NotBlank
        @Pattern(regexp = "^[0-9]{6,20}$", message = "학번은 숫자 6~20자리여야 합니다.")
        String studentNumber
) {
}
