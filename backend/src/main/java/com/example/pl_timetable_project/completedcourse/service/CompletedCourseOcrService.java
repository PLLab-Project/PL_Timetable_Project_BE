package com.example.pl_timetable_project.completedcourse.service;

import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.completedcourse.CompletedCourseErrorCode;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseOcrResponse;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 성적표 이미지를 Gemini 3.5 Flash-Lite 비전으로 인식합니다.
 *
 * <p>업로드 원본은 파일시스템이나 DB에 저장하지 않고 요청 처리 중 메모리에서만 사용합니다.
 */
@Service
public class CompletedCourseOcrService {

    private static final String PROVIDER = "GEMINI_3_5_FLASH_LITE";
    private static final String TRANSCRIPTION_PROMPT = """
            You are a precise OCR engine for university academic transcripts.
            Transcribe every visible character exactly as it appears.
            Preserve the original reading order and line breaks.
            Preserve Korean, English, numbers, symbols, course codes, grades, and credits.
            Do not infer, correct, summarize, translate, explain, or add markdown.
            Return only the transcription. If no text is visible, return an empty response.
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
                CompletedCourseOcrService::extractWithGemini);
    }

    CompletedCourseOcrService(
            boolean enabled,
            long maxFileSizeBytes,
            String projectId,
            String location,
            String model,
            int maxOutputTokens,
            GeminiTextExtractor textExtractor) {
        this.enabled = enabled;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
        this.textExtractor = textExtractor;
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
            return new CompletedCourseOcrResponse(
                    PROVIDER,
                    extractedText,
                    lines,
                    true);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw recognitionFailure(exception);
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
}
