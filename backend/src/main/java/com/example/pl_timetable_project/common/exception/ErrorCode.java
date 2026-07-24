package com.example.pl_timetable_project.common.exception;

/**
 * 모든 기능 오류 코드가 구현하는 공통 계약입니다.
 *
 * <p>각 담당자는 자신의 기능 패키지에 enum을 만들고 이 인터페이스를 구현합니다.</p>
 */
public interface ErrorCode {

    int status();

    String code();

    String message();
}
