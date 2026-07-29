package com.example.pl_timetable_project.completedcourse.service;

import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.completedcourse.CompletedCourseErrorCode;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseOcrResponse;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageContext;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 성적표 이미지를 Cloud Vision으로 인식합니다.
 *
 * <p>업로드 원본은 파일시스템이나 DB에 저장하지 않고 요청 처리 중 메모리에서만 사용합니다.
 */
@Service
public class CompletedCourseOcrService {

    private static final String PROVIDER = "GOOGLE_CLOUD_VISION";
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/bmp",
            "image/webp",
            "image/x-icon",
            "image/vnd.microsoft.icon");

    private final boolean enabled;
    private final long maxFileSizeBytes;
    private final VisionClientFactory clientFactory;

    @Autowired
    public CompletedCourseOcrService(
            @Value("${app.ocr.enabled:false}") boolean enabled,
            @Value("${app.ocr.max-file-size-bytes:10485760}") long maxFileSizeBytes) {
        this(enabled, maxFileSizeBytes, ImageAnnotatorClient::create);
    }

    CompletedCourseOcrService(
            boolean enabled,
            long maxFileSizeBytes,
            VisionClientFactory clientFactory) {
        this.enabled = enabled;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.clientFactory = clientFactory;
    }

    public CompletedCourseOcrResponse recognize(MultipartFile file) {
        validate(file);

        try {
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .setImage(Image.newBuilder()
                            .setContent(ByteString.copyFrom(file.getBytes()))
                            .build())
                    .addFeatures(Feature.newBuilder()
                            .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                            .build())
                    .setImageContext(ImageContext.newBuilder()
                            .addLanguageHints("ko")
                            .addLanguageHints("en")
                            .build())
                    .build();

            try (ImageAnnotatorClient client = clientFactory.create()) {
                BatchAnnotateImagesResponse batch = client.batchAnnotateImages(List.of(request));
                if (batch.getResponsesCount() != 1) {
                    throw recognitionFailure(null);
                }

                AnnotateImageResponse response = batch.getResponses(0);
                if (response.hasError()) {
                    throw recognitionFailure(null);
                }

                String extractedText = response.hasFullTextAnnotation()
                        ? response.getFullTextAnnotation().getText().strip()
                        : "";
                List<String> lines = extractedText.lines()
                        .map(String::strip)
                        .filter(line -> !line.isBlank())
                        .toList();
                return new CompletedCourseOcrResponse(
                        PROVIDER,
                        extractedText,
                        lines,
                        true);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw recognitionFailure(exception);
        }
    }

    private void validate(MultipartFile file) {
        if (!enabled) {
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
    interface VisionClientFactory {
        ImageAnnotatorClient create() throws IOException;
    }
}
