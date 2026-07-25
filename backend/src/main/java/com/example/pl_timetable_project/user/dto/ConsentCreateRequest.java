package com.example.pl_timetable_project.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "개인정보 처리방침 버전에 대한 동의 기록 요청")
public record ConsentCreateRequest(
        @Schema(
                description = "화면에 표시한 개인정보 처리방침의 불변 버전 식별자",
                example = "privacy-v1")
        @NotBlank
        String consentVersion,

        @Schema(description = "사용자가 해당 버전에 동의했는지 여부", example = "true")
        boolean agreed) {
}
