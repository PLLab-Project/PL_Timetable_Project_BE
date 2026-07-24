package com.example.pl_timetable_project.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void exposesItsErrorCode() {
        BusinessException exception = new BusinessException(CommonErrorCode.FORBIDDEN);

        assertThat(exception.errorCode()).isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(exception.getMessage()).isEqualTo("요청한 작업을 수행할 권한이 없습니다.");
    }
}
