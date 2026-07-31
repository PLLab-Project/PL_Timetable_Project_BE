package com.example.pl_timetable_project.completedcourse.service;

import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.completedcourse.CompletedCourseErrorCode;
import com.example.pl_timetable_project.completedcourse.CompletedCourseGradingBasis;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseOcrResponse;
import com.example.pl_timetable_project.completedcourse.dto.RecognizedCourse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 성적표 이미지를 Gemini 3.5 Flash-Lite 비전으로 인식합니다.
 *
 * <p>업로드 원본은 파일시스템이나 DB에 저장하지 않고 요청 처리 중 메모리에서만 사용합니다.
 * 전사(transcription) 이후, 전사된 텍스트를 다시 Gemini에 보내 과목명·학점 등 구조화된 정보를
 * 별도로 추출합니다. 구조화 추출이 실패하거나 형식에 맞지 않는 응답을 받으면 전사 결과는 그대로
 * 두고 recognizedCourses만 빈 목록으로 반환해, 사용자가 extractedText/lines를 보고 직접
 * 입력할 수 있도록 합니다.
 */
@Slf4j
@Service
public class CompletedCourseOcrService {

    private static final String PROVIDER = "GEMINI_3_5_FLASH_LITE";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TRANSCRIPTION_PROMPT = """
            You are a precise OCR engine for university academic transcripts.
            Transcribe every visible character exactly as it appears.
            Preserve the original reading order and line breaks.
            Preserve Korean, English, numbers, symbols, course codes, grades, and credits.
            Do not infer, correct, summarize, translate, explain, or add markdown.
            Return only the transcription. If no text is visible, return an empty response.
            """;
    private static final String COURSE_EXTRACTION_PROMPT = """
            You extract structured course records from an already-transcribed university
            transcript. The transcript text follows this instruction exactly as an OCR engine
            produced it, so it may contain OCR noise, layout artifacts, or Korean/English mixed
            text.

            Return ONLY a JSON array, with no markdown fences and no explanation. Each element
            must be an object with exactly these keys:
              - "courseName": string, the course title.
              - "credits": number, the credit value (e.g. 3, 2.5). Use null if unclear.
              - "gradingBasis": "LETTER", "PASS_FAIL", or null if unclear.
              - "category": string, the course category as written (e.g. 전공필수, 전공선택,
                교양), or null if unclear.
              - "area": string, sub-area if shown (e.g. 전공심화, 교양영역), or null if not shown.
              - "semester": string, the term as written (e.g. "2026-1"), or null if not shown.
              - "confidence": number from 0 to 1 for how confident you are the row is correct,
                or null if unsure.

            Only include rows that are clearly individual course entries. Do not invent data
            that is not present in the text. If no course rows can be identified, return [].

            Transcript text:
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

    @Autowired
    public CompletedCourseOcrService(
            @Value("${app.ocr.enabled:false}") boolean enabled,
            @Value("${app.ocr.max-file-size-bytes:7340032}") long maxFileSizeBytes,
            @Value("${app.ocr.gemini.project-id:}") String projectId,
            @Value("${app.ocr.gemini.location:global}") String location,
            @Value("${app.ocr.gemini.model:gemini-3.5-flash-lite}") String model,
            @Value("${app.ocr.gemini.max-output-tokens:8192}") int maxOutputTokens) {
        this(
                enabled,
                maxFileSizeBytes,
                projectId,
                location,
                model,
                maxOutputTokens,
                CompletedCourseOcrService::extractWithGemini,
                CompletedCourseOcrService::extractCoursesWithGemini);
    }

    CompletedCourseOcrService(
            boolean enabled,
            long maxFileSizeBytes,
            String projectId,
            String location,
            String model,
            int maxOutputTokens,
            GeminiTextExtractor textExtractor,
            GeminiCourseExtractor courseExtractor) {
        this.enabled = enabled;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.textExtractor = textExtractor;
        this.courseExtractor = courseExtractor;
    }

    public CompletedCourseOcrResponse recognize(MultipartFile file) {
        validate(file);

        try {
            String extractedText = normalizeModelText(textExtractor.extract(
                    projectId,
                    location,
                    model,
                    maxOutputTokens,
                    file.getBytes(),
                    file.getContentType()));
            List<String> lines = extractedText.lines()
                    .map(String::strip)
                    .filter(line -> !line.isBlank())
                    .toList();
            List<RecognizedCourse> recognizedCourses = recognizeCourses(extractedText);
            return new CompletedCourseOcrResponse(
                    PROVIDER,
                    extractedText,
                    lines,
                    recognizedCourses,
                    true);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw recognitionFailure(exception);
        }
    }

    private List<RecognizedCourse> recognizeCourses(String extractedText) {
        if (extractedText.isBlank()) {
            return List.of();
        }
        try {
            String json = normalizeModelText(courseExtractor.extract(
                    projectId, location, model, maxOutputTokens, extractedText));
            return parseRecognizedCourses(json);
        } catch (Exception exception) {
            log.warn("Falling back to empty recognizedCourses: structured OCR extraction failed",
                    exception);
            return List.of();
        }
    }

    private static List<RecognizedCourse> parseRecognizedCourses(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode root = OBJECT_MAPPER.readTree(json);
        JsonNode array = root.isArray() ? root : root.path("courses");
        if (!array.isArray()) {
            return List.of();
        }

        List<RecognizedCourse> courses = new ArrayList<>();
        for (JsonNode node : array) {
            String courseName = textOrNull(node, "courseName");
            if (courseName == null) {
                continue;
            }
            courses.add(new RecognizedCourse(
                    courseName,
                    decimalOrNull(node, "credits"),
                    gradingBasisOrNull(node, "gradingBasis"),
                    textOrNull(node, "category"),
                    textOrNull(node, "area"),
                    textOrNull(node, "semester"),
                    doubleOrNull(node, "confidence")));
        }
        return List.copyOf(courses);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String text = value.asText().strip();
        return text.isBlank() ? null : text;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText().strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return Double.valueOf(value.asText().strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static CompletedCourseGradingBasis gradingBasisOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        if (text == null) {
            return null;
        }
        try {
            return CompletedCourseGradingBasis.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
            String transcribedText) {
        Content content = Content.fromParts(
                Part.fromText(COURSE_EXTRACTION_PROMPT),
                Part.fromText(transcribedText));
        GenerateContentConfig config = GenerateContentConfig.builder()
                .maxOutputTokens(maxOutputTokens)
                .responseMimeType("application/json")
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
                String transcribedText) throws IOException;
    }
}
