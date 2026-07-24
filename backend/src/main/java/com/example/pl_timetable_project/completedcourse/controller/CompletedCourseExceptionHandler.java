package com.example.pl_timetable_project.completedcourse.controller;

import com.example.pl_timetable_project.common.response.ApiResponse;
import com.example.pl_timetable_project.completedcourse.CompletedCourseErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** completedcourse 요청 본문의 enum/JSON 형식 오류를 400으로 고정합니다. */
@RestControllerAdvice(assignableTypes = CompletedCourseController.class)
public class CompletedCourseExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(
            HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(CompletedCourseErrorCode.INVALID_REQUEST));
    }
}
