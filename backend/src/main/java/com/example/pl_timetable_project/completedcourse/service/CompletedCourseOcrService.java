package com.example.pl_timetable_project.completedcourse.service;

import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.completedcourse.CompletedCourseGradingBasis;
import com.example.pl_timetable_project.completedcourse.CompletedCourseErrorCode;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseOcrResponse;
import com.example.pl_timetable_project.completedcourse.dto.OcrCourseMatchStatus;
import com.example.pl_timetable_project.completedcourse.dto.OcrDocumentType;
import com.example.pl_timetable_project.completedcourse.dto.RecognizedCourseMeetingResponse;
import com.example.pl_timetable_project.completedcourse.dto.RecognizedCourseResponse;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 성적표·시간표 이미지를 Gemini 3.5 Flash-Lite 비전으로 인식합니다.
 *
 * <p>업로드 원본은 파일시스템이나 DB에 저장하지 않고 요청 처리 중 메모리에서만 사용합니다.
 */
@Service
public class CompletedCourseOcrService {

    private static final Logger log =
            LoggerFactory.getLogger(CompletedCourseOcrService.class);
    private static final String PROVIDER = "GEMINI_3_5_FLASH_LITE";
    private static final String TRANSCRIPTION_PROMPT = """
            You are a precise OCR engine for university academic documents, including
            timetable screenshots and transcripts.
            Transcribe every visible character exactly as it appears.
            Preserve the original reading order and line breaks.
            Preserve Korean, English, numbers, symbols, course codes, grades, credits,
            semester labels, professors, times, and rooms.
            Do not infer, correct, summarize, translate, explain, or add markdown.
            Return only the transcription. If no text is visible, return an empty response.
            """;
    private static final String STRUCTURING_PROMPT = """
            Inspect the university academic image directly and use the OCR transcription only
            as a secondary aid. The image may be a timetable grid or a transcript table.
            Return JSON only in exactly this top-level shape:
            {
              "documentType": "TIMETABLE or TRANSCRIPT or OTHER",
              "semester": "YYYY-1 or YYYY-2 or null",
              "recognizedCourses": [
                {
                  "courseName": "string",
                  "professor": "string or null",
                  "credits": 3.0,
                  "gradingBasis": "LETTER or PASS_FAIL",
                  "category": "string",
                  "area": "string or null",
                  "semester": "YYYY-term or null",
                  "meetings": [
                    {
                      "dayOfWeek": "MONDAY",
                      "startTime": "HH:mm",
                      "endTime": "HH:mm",
                      "room": "string or null"
                    }
                  ],
                  "confidence": 0.0
                }
              ]
            }
            The top-level semester is the academic year and term visibly printed in the image.
            Normalize visible forms such as '2026년 1학기' to '2026-1'. Never infer a semester
            from course names, current date, or general knowledge. Return null when it is not
            visible.
            Extract one item per visible course. In a timetable grid, merge repeated blocks for
            the same course into one item and derive each meeting's day and start/end time from
            the weekday column and time axis. Preserve the visible room and professor text.
            For TIMETABLE documents, include only courses that occupy an actual timetable grid
            block and have at least one meeting. Ignore course names shown only in footer text.
            Valid dayOfWeek values are MONDAY through SUNDAY.
            Use PASS_FAIL only when P/N, P/F, pass/fail, or equivalent grading is visible.
            Use LETTER only when a letter grade is visible. Otherwise gradingBasis is null.
            confidence must be between 0 and 1. Use null for fields that cannot be determined.
            Do not invent credits, category, area, professor, room, time, or semester when the
            value is not visible. Do not treat navigation, footer, or menu text as a course.
            Do not return courseCode, status, gradeValue, explanations, or markdown.
            """;
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif");

    private final boolean enabled;
    private final long maxFileSizeBytes;
    private final String projectId;
    private final String location;
    private final String model;
    private final int maxOutputTokens;
    private final GeminiTextExtractor textExtractor;
    private final GeminiCourseExtractor courseExtractor;
    private final CompletedCourseSectionMatcher sectionMatcher;
    private final ObjectMapper objectMapper;

    @Autowired
    public CompletedCourseOcrService(
            @Value("${app.ocr.enabled:false}") boolean enabled,
            @Value("${app.ocr.max-file-size-bytes:7340032}") long maxFileSizeBytes,
            @Value("${app.ocr.gemini.project-id:}") String projectId,
            @Value("${app.ocr.gemini.location:global}") String location,
            @Value("${app.ocr.gemini.model:gemini-3.5-flash-lite}") String model,
            @Value("${app.ocr.gemini.max-output-tokens:8192}") int maxOutputTokens,
            CompletedCourseSectionMatcher sectionMatcher,
            ObjectMapper objectMapper) {
        this(
                enabled,
                maxFileSizeBytes,
                projectId,
                location,
                model,
                maxOutputTokens,
                CompletedCourseOcrService::extractWithGemini,
                CompletedCourseOcrService::extractCoursesWithGemini,
                sectionMatcher,
                objectMapper);
    }

    CompletedCourseOcrService(
            boolean enabled,
            long maxFileSizeBytes,
            String projectId,
            String location,
            String model,
            int maxOutputTokens,
            GeminiTextExtractor textExtractor,
            GeminiCourseExtractor courseExtractor,
            CompletedCourseSectionMatcher sectionMatcher,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.textExtractor = textExtractor;
        this.courseExtractor = courseExtractor;
        this.sectionMatcher = sectionMatcher;
        this.objectMapper = objectMapper;
    }

    public CompletedCourseOcrResponse recognize(UUID userId, MultipartFile file) {
        validate(file);

        try {
            byte[] image = file.getBytes();
            String extractedText = normalizeModelText(textExtractor.extract(
                    projectId,
                    location,
                    model,
                    maxOutputTokens,
                    image,
                    file.getContentType()));
            List<String> lines = extractedText.lines()
                    .map(String::strip)
                    .filter(line -> !line.isBlank())
                    .toList();
            StructuredRecognition recognition = recognizeCourses(
                    image, file.getContentType(), extractedText);
            CompletedCourseSectionMatcher.MatchingResult matching = sectionMatcher.match(
                    userId,
                    recognition.documentType(),
                    recognition.recognizedSemester(),
                    recognition.courses());
            return new CompletedCourseOcrResponse(
                    PROVIDER,
                    extractedText,
                    lines,
                    recognition.documentType(),
                    recognition.recognizedSemester(),
                    matching.resolvedSemester(),
                    matching.courses(),
                    true);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw recognitionFailure(exception);
        }
    }

    CompletedCourseOcrResponse recognize(MultipartFile file) {
        return recognize(null, file);
    }

    private void validate(MultipartFile file) {
        if (!enabled) {
            throw new BusinessException(CompletedCourseErrorCode.OCR_NOT_CONFIGURED);
        }
        if (projectId.isBlank()
                || location.isBlank()
                || model.isBlank()
                || maxOutputTokens <= 0) {
            throw new BusinessException(CompletedCourseErrorCode.OCR_NOT_CONFIGURED);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CompletedCourseErrorCode.OCR_EMPTY_IMAGE);
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(CompletedCourseErrorCode.OCR_FILE_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (contentType == null
                || !SUPPORTED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(CompletedCourseErrorCode.OCR_UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private static String extractWithGemini(
            String projectId,
            String location,
            String model,
            int maxOutputTokens,
            byte[] image,
            String contentType) {
        Content content = Content.fromParts(
                Part.fromText(TRANSCRIPTION_PROMPT),
                Part.fromBytes(image, contentType));
        GenerateContentConfig config = GenerateContentConfig.builder()
                .maxOutputTokens(maxOutputTokens)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel("MINIMAL")
                        .build())
                .build();

        try (Client client = Client.builder()
                .vertexAI(true)
                .project(projectId)
                .location(location)
                .build()) {
            GenerateContentResponse response =
                    client.models.generateContent(model, content, config);
            return response.text();
        }
    }

    private static String extractCoursesWithGemini(
            String projectId,
            String location,
            String model,
            int maxOutputTokens,
            byte[] image,
            String contentType,
            String extractedText) {
        Content content = Content.fromParts(
                Part.fromText(STRUCTURING_PROMPT
                        + "\n\nOCR transcription (secondary aid):\n"
                        + extractedText),
                Part.fromBytes(image, contentType));
        GenerateContentConfig config = GenerateContentConfig.builder()
                .maxOutputTokens(maxOutputTokens)
                .responseMimeType("application/json")
                .temperature(0.1F)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel("MINIMAL")
                        .build())
                .build();

        try (Client client = Client.builder()
                .vertexAI(true)
                .project(projectId)
                .location(location)
                .build()) {
            GenerateContentResponse response =
                    client.models.generateContent(model, content, config);
            return response.text();
        }
    }

    /** 구조화 실패는 원문 OCR 성공을 무효화하지 않고 빈 후보 목록으로 낮춥니다. */
    private StructuredRecognition recognizeCourses(
            byte[] image, String contentType, String extractedText) {
        try {
            String json = normalizeModelText(courseExtractor.extract(
                    projectId,
                    location,
                    model,
                    maxOutputTokens,
                    image,
                    contentType,
                    extractedText));
            JsonNode root = objectMapper.readTree(json);
            OcrDocumentType documentType = root.isObject()
                    ? documentTypeOrOther(root.path("documentType"))
                    : OcrDocumentType.OTHER;
            String recognizedSemester = root.isObject()
                    ? CompletedCourseSectionMatcher.normalizeSemester(
                            textOrNull(root.path("semester")))
                    : null;
            JsonNode courseNodes = root.isArray()
                    ? root
                    : root.path("recognizedCourses");
            if (!courseNodes.isArray()) {
                return new StructuredRecognition(
                        documentType, recognizedSemester, List.of());
            }

            Map<String, RecognizedCourseResponse> courses = new LinkedHashMap<>();
            for (JsonNode courseNode : courseNodes) {
                String courseName = textOrNull(courseNode.path("courseName"));
                String normalizedCourseName =
                        CompletedCourseSectionMatcher.normalizeKey(courseName);
                if (courseName == null
                        || normalizedCourseName.isEmpty()) {
                    continue;
                }
                String courseSemester = CompletedCourseSectionMatcher.normalizeSemester(
                        textOrNull(courseNode.path("semester")));
                if (courseSemester == null) {
                    courseSemester = recognizedSemester;
                }
                RecognizedCourseResponse parsed = new RecognizedCourseResponse(
                        courseName,
                        decimalOrNull(courseNode.path("credits")),
                        gradingBasisOrNull(courseNode.path("gradingBasis")),
                        visibleTextOrNull(extractedText, courseNode.path("category")),
                        visibleTextOrNull(extractedText, courseNode.path("area")),
                        courseSemester,
                        confidenceOrNull(courseNode.path("confidence")),
                        visibleTextOrNull(extractedText, courseNode.path("professor")),
                        meetings(extractedText, courseNode.path("meetings")),
                        OcrCourseMatchStatus.UNMATCHED,
                        null,
                        List.of());
                if (documentType == OcrDocumentType.TIMETABLE
                        && parsed.meetings().isEmpty()) {
                    continue;
                }
                courses.merge(
                        normalizedCourseName,
                        parsed,
                        CompletedCourseOcrService::mergeCourse);
            }
            return new StructuredRecognition(
                    documentType,
                    recognizedSemester,
                    List.copyOf(courses.values()));
        } catch (Exception exception) {
            // 성적표 원문이나 모델 응답이 로그에 노출되지 않도록 오류 종류만 기록합니다.
            log.warn(
                    "OCR 원문 구조화에 실패해 빈 과목 후보를 반환합니다. errorType={}",
                    exception.getClass().getSimpleName());
            return StructuredRecognition.EMPTY;
        }
    }

    private static List<RecognizedCourseMeetingResponse> meetings(
            String extractedText, JsonNode meetingNodes) {
        if (!meetingNodes.isArray()) {
            return List.of();
        }
        List<RecognizedCourseMeetingResponse> meetings = new ArrayList<>();
        for (JsonNode meetingNode : meetingNodes) {
            var dayOfWeek = dayOfWeekOrNull(meetingNode.path("dayOfWeek"));
            var startTime = timeOrNull(meetingNode.path("startTime"));
            var endTime = timeOrNull(meetingNode.path("endTime"));
            String room = visibleTextOrNull(extractedText, meetingNode.path("room"));
            if (dayOfWeek == null && startTime == null && endTime == null && room == null) {
                continue;
            }
            RecognizedCourseMeetingResponse meeting =
                    new RecognizedCourseMeetingResponse(
                            dayOfWeek, startTime, endTime, room);
            if (!meetings.contains(meeting)) {
                meetings.add(meeting);
            }
        }
        return List.copyOf(meetings);
    }

    private static java.time.DayOfWeek dayOfWeekOrNull(JsonNode node) {
        String value = textOrNull(node);
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT).strip();
        return switch (normalized) {
            case "MONDAY", "MON", "월", "월요일" -> java.time.DayOfWeek.MONDAY;
            case "TUESDAY", "TUE", "화", "화요일" -> java.time.DayOfWeek.TUESDAY;
            case "WEDNESDAY", "WED", "수", "수요일" -> java.time.DayOfWeek.WEDNESDAY;
            case "THURSDAY", "THU", "목", "목요일" -> java.time.DayOfWeek.THURSDAY;
            case "FRIDAY", "FRI", "금", "금요일" -> java.time.DayOfWeek.FRIDAY;
            case "SATURDAY", "SAT", "토", "토요일" -> java.time.DayOfWeek.SATURDAY;
            case "SUNDAY", "SUN", "일", "일요일" -> java.time.DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private static OcrDocumentType documentTypeOrOther(JsonNode node) {
        String value = textOrNull(node);
        if (value == null) {
            return OcrDocumentType.OTHER;
        }
        try {
            return OcrDocumentType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return OcrDocumentType.OTHER;
        }
    }

    private static java.time.LocalTime timeOrNull(JsonNode node) {
        String value = textOrNull(node);
        if (value == null) {
            return null;
        }
        try {
            return java.time.LocalTime.parse(
                    value.length() == 4 ? "0" + value : value);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private static String visibleTextOrNull(String extractedText, JsonNode node) {
        String value = textOrNull(node);
        if (value == null) {
            return null;
        }
        if (extractedText.isBlank()) {
            return value;
        }
        String normalizedValue = CompletedCourseSectionMatcher.normalizeKey(value);
        return !normalizedValue.isEmpty()
                        && CompletedCourseSectionMatcher.normalizeKey(extractedText)
                                .contains(normalizedValue)
                ? value
                : null;
    }

    private static RecognizedCourseResponse mergeCourse(
            RecognizedCourseResponse first, RecognizedCourseResponse second) {
        List<RecognizedCourseMeetingResponse> mergedMeetings =
                new ArrayList<>(first.meetings());
        second.meetings().stream()
                .filter(meeting -> !mergedMeetings.contains(meeting))
                .forEach(mergedMeetings::add);
        return new RecognizedCourseResponse(
                first.courseName(),
                first.credits() != null ? first.credits() : second.credits(),
                first.gradingBasis() != null
                        ? first.gradingBasis()
                        : second.gradingBasis(),
                first.category() != null ? first.category() : second.category(),
                first.area() != null ? first.area() : second.area(),
                first.semester() != null ? first.semester() : second.semester(),
                max(first.confidence(), second.confidence()),
                first.professor() != null ? first.professor() : second.professor(),
                List.copyOf(mergedMeetings),
                OcrCourseMatchStatus.UNMATCHED,
                null,
                List.of());
    }

    private static BigDecimal max(BigDecimal first, BigDecimal second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.max(second);
    }

    private static String textOrNull(JsonNode node) {
        String value = node.asString("").strip();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private static BigDecimal decimalOrNull(JsonNode node) {
        try {
            if (node.isNumber()) {
                return node.decimalValue();
            }
            String value = textOrNull(node);
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static CompletedCourseGradingBasis gradingBasisOrNull(JsonNode node) {
        String value = textOrNull(node);
        if (value == null) {
            return null;
        }
        String normalized = value.toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace('/', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "LETTER" -> CompletedCourseGradingBasis.LETTER;
            case "PASS_FAIL", "P_F", "PN", "P_N" ->
                    CompletedCourseGradingBasis.PASS_FAIL;
            default -> null;
        };
    }

    private static BigDecimal confidenceOrNull(JsonNode node) {
        BigDecimal value = decimalOrNull(node);
        if (value == null) {
            return null;
        }
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private static String normalizeModelText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.strip();
        if (!normalized.startsWith("```") || !normalized.endsWith("```")) {
            return normalized;
        }

        int firstLineBreak = normalized.indexOf('\n');
        if (firstLineBreak < 0) {
            return "";
        }
        return normalized.substring(firstLineBreak + 1, normalized.length() - 3).strip();
    }

    private static BusinessException recognitionFailure(Throwable cause) {
        String message = CompletedCourseErrorCode.OCR_RECOGNITION_FAILED.message();
        return cause == null
                ? new BusinessException(CompletedCourseErrorCode.OCR_RECOGNITION_FAILED, message)
                : new BusinessException(
                        CompletedCourseErrorCode.OCR_RECOGNITION_FAILED,
                        message,
                        cause);
    }

    @FunctionalInterface
    interface GeminiTextExtractor {
        String extract(
                String projectId,
                String location,
                String model,
                int maxOutputTokens,
                byte[] image,
                String contentType) throws IOException;
    }

    @FunctionalInterface
    interface GeminiCourseExtractor {
        String extract(
                String projectId,
                String location,
                String model,
                int maxOutputTokens,
                byte[] image,
                String contentType,
                String extractedText) throws IOException;
    }

    private record StructuredRecognition(
            OcrDocumentType documentType,
            String recognizedSemester,
            List<RecognizedCourseResponse> courses) {

        private static final StructuredRecognition EMPTY =
                new StructuredRecognition(OcrDocumentType.OTHER, null, List.of());
    }
}
