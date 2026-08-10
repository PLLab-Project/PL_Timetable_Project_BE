package com.example.pl_timetable_project.completedcourse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.completedcourse.CompletedCourseErrorCode;
import com.example.pl_timetable_project.completedcourse.CompletedCourseGradingBasis;
import com.example.pl_timetable_project.completedcourse.dto.OcrDocumentType;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

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
        assertThat(response.documentType()).isEqualTo(OcrDocumentType.OTHER);
        assertThat(response.recognizedSemester()).isNull();
        assertThat(response.resolvedSemester()).isNull();
        assertThat(response.recognizedCourses()).isEmpty();
        assertThat(response.requiresConfirmation()).isTrue();
    }

    @Test
    void returnsStructuredCoursesFromSecondGeminiCall() {
        AtomicReference<String> transcriptionUsed = new AtomicReference<>();
        CompletedCourseOcrService service = service(
                true,
                10_000,
                (project, location, model, tokens, bytes, contentType) ->
                        "2026년 1학기 전공선택 전공심화 자료구조 박정규 월 15:30 17:30 공다A 411 3 A+",
                (project, location, model, tokens, bytes, contentType, transcription) -> {
                    assertThat(bytes).containsExactly(1);
                    assertThat(contentType).isEqualTo("image/png");
                    transcriptionUsed.set(transcription);
                    return """
                            {
                              "documentType": "TIMETABLE",
                              "semester": "2026년 1학기",
                              "recognizedCourses": [
                                {
                                  "courseName": "자료구조",
                                  "professor": "박정규",
                                  "credits": 3.0,
                                  "gradingBasis": "LETTER",
                                  "category": "전공선택",
                                  "area": "전공심화",
                                  "semester": null,
                                  "meetings": [
                                    {
                                      "dayOfWeek": "MONDAY",
                                      "startTime": "15:30",
                                      "endTime": "17:30",
                                      "room": "공다A 411"
                                    }
                                  ],
                                  "confidence": 0.94
                                }
                              ]
                            }
                            """;
                });

        var response = service.recognize(image("image/png", new byte[] {1}));

        assertThat(transcriptionUsed).hasValue(
                "2026년 1학기 전공선택 전공심화 자료구조 박정규 월 15:30 17:30 공다A 411 3 A+");
        assertThat(response.recognizedSemester()).isEqualTo("2026-1");
        assertThat(response.resolvedSemester()).isEqualTo("2026-1");
        assertThat(response.documentType()).isEqualTo(OcrDocumentType.TIMETABLE);
        assertThat(response.recognizedCourses()).singleElement().satisfies(course -> {
            assertThat(course.courseName()).isEqualTo("자료구조");
            assertThat(course.credits()).isEqualByComparingTo("3.0");
            assertThat(course.gradingBasis()).isEqualTo(CompletedCourseGradingBasis.LETTER);
            assertThat(course.category()).isEqualTo("전공선택");
            assertThat(course.area()).isEqualTo("전공심화");
            assertThat(course.semester()).isEqualTo("2026-1");
            assertThat(course.confidence()).isEqualByComparingTo("0.94");
            assertThat(course.professor()).isEqualTo("박정규");
            assertThat(course.meetings()).singleElement().satisfies(meeting -> {
                assertThat(meeting.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
                assertThat(meeting.startTime()).isEqualTo(LocalTime.of(15, 30));
                assertThat(meeting.endTime()).isEqualTo(LocalTime.of(17, 30));
                assertThat(meeting.room()).isEqualTo("공다A 411");
            });
        });
    }

    @Test
    void keepsTimetableCourseWithoutMeetingsAndFlagsItForTimeConfirmation() {
        // 예전에는 TIMETABLE 문서에서 meetings가 비면 조용히 삭제했다. 온라인
        // 강의처럼 그리드 밖 별도 목록으로 표시돼 요일·시간을 못 읽은 경우가 이
        // 케이스에 해당하는데, 이제는 지우지 않고 needsTimeConfirmation=true로
        // 남겨 사용자가 직접 확인하게 한다.
        CompletedCourseOcrService service = service(
                true,
                10_000,
                (project, location, model, tokens, bytes, contentType) ->
                        "2026년 1학기 온라인강의 자기주도학습",
                (project, location, model, tokens, bytes, contentType, transcription) -> """
                        {
                          "documentType": "TIMETABLE",
                          "semester": "2026년 1학기",
                          "recognizedCourses": [
                            {
                              "courseName": "온라인강의",
                              "professor": null,
                              "credits": null,
                              "gradingBasis": null,
                              "category": null,
                              "area": null,
                              "semester": null,
                              "meetings": [],
                              "confidence": 0.7
                            }
                          ]
                        }
                        """);

        var response = service.recognize(image("image/png", new byte[] {1}));

        assertThat(response.recognizedCourses()).singleElement().satisfies(course -> {
            assertThat(course.courseName()).isEqualTo("온라인강의");
            assertThat(course.meetings()).isEmpty();
            assertThat(course.needsTimeConfirmation()).isTrue();
        });
    }

    @Test
    void marksMeetingAsOnlineWhenRoomTextContainsOnlineKeyword() {
        CompletedCourseOcrService service = service(
                true,
                10_000,
                (project, location, model, tokens, bytes, contentType) ->
                        "2026년 1학기 회계원리 장석진 월17:30-18:25 e-learning(온라인)1",
                (project, location, model, tokens, bytes, contentType, transcription) -> """
                        {
                          "documentType": "TIMETABLE",
                          "semester": "2026년 1학기",
                          "recognizedCourses": [
                            {
                              "courseName": "회계원리",
                              "professor": "장석진",
                              "credits": 3.0,
                              "gradingBasis": null,
                              "category": null,
                              "area": null,
                              "semester": null,
                              "meetings": [
                                {
                                  "dayOfWeek": "MONDAY",
                                  "startTime": "17:30",
                                  "endTime": "18:25",
                                  "room": "e-learning(온라인)1"
                                }
                              ],
                              "confidence": 0.9
                            }
                          ]
                        }
                        """);

        var response = service.recognize(image("image/png", new byte[] {1}));

        assertThat(response.recognizedCourses()).singleElement().satisfies(course -> {
            // 요일·시간이 정상적으로 있으므로 확인이 더 필요하진 않다.
            assertThat(course.needsTimeConfirmation()).isFalse();
            assertThat(course.meetings()).singleElement().satisfies(meeting -> {
                assertThat(meeting.room()).isEqualTo("e-learning(온라인)1");
                assertThat(meeting.online()).isTrue();
            });
        });
    }

    @Test
    void doesNotFlagTranscriptCourseWithoutMeetingsForTimeConfirmation() {
        // 성적표는 애초에 요일·시간 정보가 없는 문서라, meetings가 비어도
        // needsTimeConfirmation은 항상 false여야 한다(TIMETABLE 전용 신호).
        CompletedCourseOcrService service = service(
                true,
                10_000,
                (project, location, model, tokens, bytes, contentType) -> "자료구조 3학점 A+",
                (project, location, model, tokens, bytes, contentType, transcription) -> """
                        {
                          "documentType": "TRANSCRIPT",
                          "semester": null,
                          "recognizedCourses": [
                            {
                              "courseName": "자료구조",
                              "professor": null,
                              "credits": 3.0,
                              "gradingBasis": "LETTER",
                              "category": null,
                              "area": null,
                              "semester": null,
                              "meetings": [],
                              "confidence": 0.8
                            }
                          ]
                        }
                        """);

        var response = service.recognize(image("image/png", new byte[] {1}));

        assertThat(response.recognizedCourses()).singleElement().satisfies(course -> {
            assertThat(course.meetings()).isEmpty();
            assertThat(course.needsTimeConfirmation()).isFalse();
        });
    }

    @Test
    void keepsTranscriptionWhenStructuredExtractionFails() {
        CompletedCourseOcrService service = service(
                true,
                10_000,
                (project, location, model, tokens, bytes, contentType) ->
                        "자료구조 3학점",
                (project, location, model, tokens, bytes, contentType, transcription) ->
                        "not-json");

        var response = service.recognize(image("image/png", new byte[] {1}));

        assertThat(response.extractedText()).isEqualTo("자료구조 3학점");
        assertThat(response.lines()).containsExactly("자료구조 3학점");
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
                (project, location, model, tokens, bytes, contentType, transcription) ->
                        "{\"documentType\":\"OTHER\",\"semester\":null,"
                                + "\"recognizedCourses\":[]}");
    }

    private static CompletedCourseOcrService service(
            boolean enabled,
            long maxFileSizeBytes,
            CompletedCourseOcrService.GeminiTextExtractor textExtractor,
            CompletedCourseOcrService.GeminiCourseExtractor courseExtractor) {
        return new CompletedCourseOcrService(
                enabled,
                maxFileSizeBytes,
                "pl-timetable-project",
                "global",
                "gemini-3.5-flash-lite",
                8192,
                textExtractor,
                courseExtractor,
                passthroughMatcher(),
                new ObjectMapper());
    }

    private static CompletedCourseSectionMatcher passthroughMatcher() {
        CompletedCourseSectionMatcher matcher = mock(CompletedCourseSectionMatcher.class);
        when(matcher.match(
                nullable(java.util.UUID.class),
                nullable(OcrDocumentType.class),
                nullable(String.class),
                anyList()))
                .thenAnswer(invocation ->
                new CompletedCourseSectionMatcher.MatchingResult(
                        invocation.getArgument(2), invocation.getArgument(3)));
        return matcher;
    }

    private static void assertBusinessError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            CompletedCourseErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
