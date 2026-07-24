package com.example.pl_timetable_project.academic.review;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.common.TextQuery;
import com.example.pl_timetable_project.academic.review.ReviewRepository.CourseOffering;
import com.example.pl_timetable_project.academic.review.dto.ReviewCreateRequest;
import com.example.pl_timetable_project.academic.review.dto.ReviewResponse;
import com.example.pl_timetable_project.academic.review.dto.ReviewUpdateRequest;
import com.example.pl_timetable_project.exception.AcademicResourceNotFoundException;
import com.example.pl_timetable_project.exception.InvalidAcademicQueryException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository repository;

    public ReviewService(ReviewRepository repository) {
        this.repository = repository;
    }

    public AcademicPageResponse<ReviewResponse> listPublic(
            String semesterId,
            String courseCode,
            String professor,
            int page,
            int size) {
        String normalizedSemesterId = optional(
                semesterId, 20, "학기 ID");
        String normalizedCourseCode = optional(
                courseCode, 40, "과목 코드");
        String normalizedProfessor = optional(
                professor, 120, "교수명");
        PageSpec pageSpec = PageSpec.of(page, size);
        return AcademicPageResponse.of(
                repository.findPublic(
                        normalizedSemesterId,
                        normalizedCourseCode,
                        normalizedProfessor,
                        pageSpec),
                pageSpec,
                repository.countPublic(
                        normalizedSemesterId,
                        normalizedCourseCode,
                        normalizedProfessor));
    }

    public AcademicPageResponse<ReviewResponse> listMine(
            UUID userId, String semesterId, int page, int size) {
        String normalizedSemesterId = optional(
                semesterId, 20, "학기 ID");
        PageSpec pageSpec = PageSpec.of(page, size);
        return AcademicPageResponse.of(
                repository.findMine(userId, normalizedSemesterId, pageSpec),
                pageSpec,
                repository.countMine(userId, normalizedSemesterId));
    }

    @Transactional
    public ReviewResponse create(UUID userId, ReviewCreateRequest request) {
        String semesterId = required(
                request.semesterId(), 20, "학기 ID");
        String courseCode = required(
                request.courseCode(), 40, "과목 코드");
        String professor = optional(
                request.professor(), 120, "교수명");
        String content = TextQuery.required(
                request.content(), "리뷰 내용");

        CourseOffering course = repository.findCourseOffering(
                        semesterId, courseCode)
                .orElseThrow(() -> new AcademicResourceNotFoundException(
                        "강의를 찾을 수 없습니다. key="
                                + semesterId + ":" + courseCode));
        if (professor != null
                && !repository.professorExists(
                        semesterId, courseCode, professor)) {
            throw new InvalidAcademicQueryException(
                    "해당 강의의 교수를 찾을 수 없습니다. professor=" + professor);
        }
        if (repository.duplicateExists(
                userId, semesterId, courseCode, professor)) {
            throw duplicateReview();
        }

        try {
            return repository.create(
                    userId,
                    course,
                    professor,
                    semesterId,
                    request.rating(),
                    content);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateReview();
        }
    }

    @Transactional
    public ReviewResponse update(
            UUID userId, UUID reviewId, ReviewUpdateRequest request) {
        String content = TextQuery.required(
                request.content(), "리뷰 내용");
        return repository.update(
                        reviewId, userId, request.rating(), content)
                .orElseThrow(() -> reviewNotFound(reviewId));
    }

    @Transactional
    public void delete(UUID userId, UUID reviewId) {
        if (!repository.delete(reviewId, userId)) {
            throw reviewNotFound(reviewId);
        }
    }

    private String required(
            String value, int maxLength, String fieldName) {
        String normalized = TextQuery.required(value, fieldName);
        validateLength(normalized, maxLength, fieldName);
        return normalized;
    }

    private String optional(
            String value, int maxLength, String fieldName) {
        String normalized = TextQuery.optional(value);
        if (normalized != null) {
            validateLength(normalized, maxLength, fieldName);
        }
        return normalized;
    }

    private void validateLength(
            String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new InvalidAcademicQueryException(
                    fieldName + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
    }

    private AcademicResourceNotFoundException reviewNotFound(UUID reviewId) {
        return new AcademicResourceNotFoundException(
                "리뷰를 찾을 수 없습니다. reviewId=" + reviewId);
    }

    private InvalidAcademicQueryException duplicateReview() {
        return new InvalidAcademicQueryException(
                "같은 학기, 과목, 교수에 대한 리뷰는 한 번만 작성할 수 있습니다.");
    }
}
