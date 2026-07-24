package com.example.pl_timetable_project.common.exception;

import com.example.pl_timetable_project.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 인증·회원 도메인의 비즈니스 예외만 처리합니다.
 * 시간표 도메인의 검증·서버 예외는 기존 공통 예외 처리기가 담당합니다.
 */
@RestControllerAdvice(name = "authGlobalExceptionHandler")
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity.status(errorCode.status()).body(ApiResponse.error(errorCode));
    }
}
