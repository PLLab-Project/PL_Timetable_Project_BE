package com.example.pl_timetable_project.completedcourse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.completedcourse.CompletedCourseErrorCode;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.TextAnnotation;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CompletedCourseOcrServiceTest {

    @Test
    void rejectsRequestWhenOcrIsDisabled() {
        CompletedCourseOcrService service =
                new CompletedCourseOcrService(false, 10_000, () -> {
                    throw new AssertionError("client must not be created");
                });

        assertBusinessError(
                () -> service.recognize(image("image/png", new byte[] {1})),
                CompletedCourseErrorCode.OCR_NOT_CONFIGURED);
    }

    @Test
    void rejectsEmptyOversizedAndUnsupportedFilesBeforeCallingVision() {
        CompletedCourseOcrService service =
                new CompletedCourseOcrService(true, 2, () -> {
                    throw new AssertionError("client must not be created");
                });

        assertBusinessError(
                () -> service.recognize(image("image/png", new byte[0])),
                CompletedCourseErrorCode.OCR_EMPTY_IMAGE);
        assertBusinessError(
                () -> service.recognize(image("image/png", new byte[] {1, 2, 3})),
                CompletedCourseErrorCode.OCR_FILE_TOO_LARGE);
        assertBusinessError(
                () -> service.recognize(image("application/pdf", new byte[] {1})),
                CompletedCourseErrorCode.OCR_UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void returnsNormalizedLinesAndClosesVisionClient() throws IOException {
        ImageAnnotatorClient client = mock(ImageAnnotatorClient.class);
        when(client.batchAnnotateImages(anyList())).thenReturn(
                BatchAnnotateImagesResponse.newBuilder()
                        .addResponses(AnnotateImageResponse.newBuilder()
                                .setFullTextAnnotation(TextAnnotation.newBuilder()
                                        .setText(" 과목코드  과목명\n\n855121  자료구조 \n")
                                        .build())
                                .build())
                        .build());
        CompletedCourseOcrService service =
                new CompletedCourseOcrService(true, 10_000, () -> client);

        var response = service.recognize(image("image/jpeg", new byte[] {1, 2, 3}));

        assertThat(response.provider()).isEqualTo("GOOGLE_CLOUD_VISION");
        assertThat(response.extractedText()).isEqualTo("과목코드  과목명\n\n855121  자료구조");
        assertThat(response.lines()).containsExactly("과목코드  과목명", "855121  자료구조");
        assertThat(response.requiresConfirmation()).isTrue();
        verify(client).close();
    }

    private static MockMultipartFile image(String contentType, byte[] content) {
        return new MockMultipartFile("file", "transcript", contentType, content);
    }

    private static void assertBusinessError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            CompletedCourseErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
