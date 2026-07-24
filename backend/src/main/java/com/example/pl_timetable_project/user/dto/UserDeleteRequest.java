package com.example.pl_timetable_project.user.dto;

/** 실수로 탈퇴 API가 호출되는 것을 막기 위한 명시적 확인 값입니다. */
public record UserDeleteRequest(boolean confirmed) {
}
