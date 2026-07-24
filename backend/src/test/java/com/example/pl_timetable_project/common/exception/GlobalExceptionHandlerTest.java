package com.example.pl_timetable_project.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pl_timetable_project.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void convertsBusinessExceptionToApiResponse() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new BusinessException(CommonErrorCode.FORBIDDEN)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COMMON_FORBIDDEN");
    }
}
