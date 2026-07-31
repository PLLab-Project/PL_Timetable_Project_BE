package com.example.pl_timetable_project.completedcourse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.completedcourse.CompletedCourseErrorCode;
import com.example.pl_timetable_project.completedcourse.CompletedCourseGradingBasis;
import com.example.pl_timetable_project.completedcourse.dto.RecognizedCourse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CompletedCourseOcrServiceTest {

    @Test
    void rejectsRequestWhenOcrIsDisabled() {
        CompletedCourseOcrService service =
                service(false, 10_000, (project, location, model, tokens, bytes, type) -> {
                    throw new AssertionError("Gemini must not be called");
                });

        assertBusinessError(
                () -> service.recognize(image("image/png", new byte[] {1})),
                CompletedCourseErrorCode.OCR_NOT_CONFIGURED);
    }

    @Test
    void rejectsEmptyOversizedAndUnsupportedFilesBeforeCallingGemini() {
        CompletedCourseOcrService service =
                service(true, 2, (project, location, model, tokens, bytes, type) -> {
                    throw new AssertionError("Gemini must not be called");
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
    void returnsNormalizedLinesFromGeminiTranscription() {
        AtomicReference<String> modelUsed = new AtomicReference<>();
        AtomicReference<String> contentTypeUsed = new AtomicReference<>();
        CompletedCourseOcrService service = service(
                true,
                10_000,
                (project, location, model, tokens, bytes, contentType) -> {
                    assertThat(project).isEqualTo("pl-timetable-project");
                    assertThat(location).isEqualTo("global");
                    assertThat(tokens).isEqualTo(8192);
                    assertThat(bytes).containsExactly(1, 2, 3);
                    modelUsed.set(model);
                    contentTypeUsed.set(contentType);
                    return "```text\n 과목코드  과목명\n\n855121  자료구조 \n```";
                });

        var response = service.recognize(image("image/jpeg", new byte[] {1, 2, 3}));

        assertThat(modelUsed).hasValue("gemini-3.5-flash-lite");
        assertThat(contentTypeUsed).hasValue("image/jpeg");
        assertThat(response.provider()).isEqualTo("GEMINI_3_5_FLASH_LITE");
        assertThat(response.extractedText()).isEqualTo("과목코드  과목명\n\n855121  자료구조");
        assertThat(response.lines()).containsExactly("과목코드  과목명", "855121  자료구조");
        assertThat(response.recognizedCourses()).isEmpty();
        assertThat(response.requiresConfirmation()).isTrue();
    }

    @Test
    void mapsGeminiFailureToStableBusinessError() {
        CompletedCourseOcrService service =
                service(true, 10_000, (project, location, model, tokens, bytes, type) -> {
                    throw new IOException("upstream unavailable");
                });

        assertBusinessError(
                () -> service.recognize(image("image/png", new byte[] {1})),
                CompletedCourseErrorCode.OCR_RECOGNITION_FAILED);
    }

    @Test
    void parsesStructuredCoursesFromGeminiJsonResponse() {
        CompletedCourseOcrService service = service(
                true,
                10_000,
                (project, location, model, tokens, bytes, type) -> "855121  자료구조  3  전공선택",
                (project, location, model, tokens, text) -> """
                        ```json
                        [
                          {
                            "courseName": "자료구조",
                            "credits": 3,
                            "gradingBasis": "LETTER",
                            "category": "전공선택",
                            "area": "전공심화",
                            "semester": "2026-1",
                            "confidence": 0.91
                          }
                        ]
                        ```
                        """);

        var response = service.recognize(image("image/png", new byte[] {1}));

        assertThat(response.recognizedCourses()).hasSize(1);
        RecognizedCourse course = response.recognizedCourses().get(0);
        assertThat(course.courseName()).isEqualTo("자료구조");
        assertThat(course.credits()).isEqualByComparingTo("3");
        assertThat(course.gradingBasis()).isEqualTo(CompletedCourseGradingBasis.LETTER);
        assertThat(course.category()).isEqualTo("전공선택");
        assertThat(course.area()).isEqualTo("전공심화");
        assertThat(course.semester()).isEqualTo("2026-1");
        assertThat(course.confidence()).isEqualTo(0.91);
        assertThat(response.requiresConfirmation()).isTrue();
    }

    @Test
    void fallsBackToEmptyRecognizedCoursesWhenGeminiReturnsMalformedJson() {
        CompletedCourseOcrService service = service(
                true,
                10_000,
                (project, location, model, tokens, bytes, type) -> "855121  자료구조  3  전공선택",
                (project, location, model, tokens, text) -> "이건 JSON이 아닙니다.");

        var response = service.recognize(image("image/png", new byte[] {1}));

        assertThat(response.extractedText()).isEqualTo("855121  자료구조  3  전공선택");
        assertThat(response.recognizedCourses()).isEmpty();
        assertThat(response.requiresConfirmation()).isTrue();
    }

    @Test
    void fallsBackToEmptyRecognizedCoursesWhenStructuredExtractionCallFails() {
        CompletedCourseOcrService service = service(
                true,
                10_000,
                (project, location, model, tokens, bytes, type) -> "855121  자료구조  3  전공선택",
                (project, location, model, tokens, text) -> {
                    throw new IOException("upstream unavailable");
                });

        var response = service.recognize(image("image/png", new byte[] {1}));

        assertThat(response.extractedText()).isEqualTo("855121  자료구조  3  전공선택");
        assertThat(response.recognizedCourses()).isEmpty();
        assertThat(response.requiresConfirmation()).isTrue();
    }

    private static MockMultipartFile image(String contentType, byte[] content) {
        return new MockMultipartFile("file", "transcript", contentType, content);
    }

    private static CompletedCourseOcrService service(
            boolean enabled,
            long maxFileSizeBytes,
            CompletedCourseOcrService.GeminiTextExtractor extractor) {
        return service(
                enabled,
                maxFileSizeBytes,
                extractor,
                (project, location, model, tokens, text) -> "[]");
    }

    private static CompletedCourseOcrService service(
            boolean enabled,
            long maxFileSizeBytes,
            CompletedCourseOcrService.GeminiTextExtractor extractor,
            CompletedCourseOcrService.GeminiCourseExtractor courseExtractor) {
        return new CompletedCourseOcrService(
                enabled,
                maxFileSizeBytes,
                "pl-timetable-project",
                "global",
                "gemini-3.5-flash-lite",
                8192,
                extractor,
                courseExtractor);
    }

    private static void assertBusinessError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            CompletedCourseErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
