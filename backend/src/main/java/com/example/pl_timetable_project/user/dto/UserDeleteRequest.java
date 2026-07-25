package com.example.pl_timetable_project.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 실수로 탈퇴 API가 호출되는 것을 막기 위한 명시적 확인 값입니다. */
@Schema(description = "회원 탈퇴 확인 요청")
public record UserDeleteRequest(
        @Schema(
                description = "탈퇴와 사용자 소유 데이터 삭제에 동의하면 반드시 true",
                example = "true")
        boolean confirmed) {
}
