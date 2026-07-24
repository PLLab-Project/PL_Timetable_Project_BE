package com.example.pl_timetable_project.auth.service;

/** OTP 전달 수단을 이메일 구현과 분리해 테스트와 운영 구현을 교체할 수 있게 합니다. */
public interface OtpSender {

    void send(String email, String code);
}
