package com.example.pl_timetable_project.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pl_timetable_project.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    @Test
    void convertsMultipartBoundaryFailuresToStableApiErrors() {
        ResponseEntity<ApiResponse<Void>> missing =
                handler.handleMissingPart(new MissingServletRequestPartException("file"));
        ResponseEntity<ApiResponse<Void>> oversized =
                handler.handleOversizedUpload(new MaxUploadSizeExceededException(10));

        assertThat(missing.getStatusCode().value()).isEqualTo(400);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(oversized.getStatusCode().value()).isEqualTo(413);
        assertThat(oversized.getBody()).isNotNull();
        assertThat(oversized.getBody().code())
                .isEqualTo("COMPLETED_COURSE_OCR_FILE_TOO_LARGE");
    }
}
