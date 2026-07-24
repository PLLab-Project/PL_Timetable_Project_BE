package com.example.pl_timetable_project.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pl_timetable_project.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successResponseContainsData() {
        ApiResponse<String> response = ApiResponse.success("result");

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.data()).isEqualTo("result");
    }

    @Test
    void errorResponseContainsErrorCodeWithoutData() {
        ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.UNAUTHORIZED);

        assertThat(response.code()).isEqualTo("UNAUTHORIZED");
        assertThat(response.message()).isEqualTo("인증이 필요합니다.");
        assertThat(response.data()).isNull();
    }
}
