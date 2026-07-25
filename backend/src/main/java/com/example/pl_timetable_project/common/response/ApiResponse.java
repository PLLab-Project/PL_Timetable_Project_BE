package com.example.pl_timetable_project.common.response;

import com.example.pl_timetable_project.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/** API의 성공·실패 응답 형식을 통일합니다. */
@Schema(description = "모든 API가 공통으로 사용하는 응답 envelope")
public record ApiResponse<T>(
        @Schema(
                description = "프론트 분기에 사용하는 안정적인 결과 코드",
                example = "SUCCESS")
        String code,

        @Schema(
                description = "사용자 또는 개발자에게 보여줄 결과 설명",
                example = "요청을 성공적으로 처리했습니다.")
        String message,

        @Schema(description = "API별 실제 응답 데이터. 데이터가 없는 성공이나 오류는 null")
        T data) {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String SUCCESS_MESSAGE = "요청을 성공적으로 처리했습니다.";

    public ApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new ApiResponse<>(
                errorCode.code(),
                Objects.requireNonNull(message, "message must not be null"),
                null);
    }
}
