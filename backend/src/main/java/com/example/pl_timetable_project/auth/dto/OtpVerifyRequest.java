package com.example.pl_timetable_project.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "학교 이메일 OTP 검증 및 로그인 요청")
public record OtpVerifyRequest(
        @Schema(
                description = "OTP를 요청할 때 사용한 숫자 학번",
                example = "20201234")
        @NotBlank
        @Pattern(regexp = "^[0-9]{6,20}$")
        String studentNumber,

        @Schema(
                description = "학교 이메일로 받은 6자리 일회용 인증번호",
                example = "123456")
        @NotBlank
        @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 숫자 6자리여야 합니다.")
        String code
) {
}
