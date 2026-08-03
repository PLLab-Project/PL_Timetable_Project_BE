package com.example.pl_timetable_project.academic.course;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.common.TextQuery;
import com.example.pl_timetable_project.academic.course.dto.CourseDetailResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseSummaryResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionDetailResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionSearchResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionSummaryResponse;
import com.example.pl_timetable_project.exception.AcademicResourceNotFoundException;
import com.example.pl_timetable_project.exception.InvalidAcademicQueryException;
import com.example.pl_timetable_project.user.repository.StudentAcademicProgramRepository;
import com.example.pl_timetable_project.user.repository.StudentProfileRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseQueryRepository repository;
    private final SectionQueryRepository sectionRepository;
    private final SectionSearchQueryRepository sectionSearchRepository;
    private final StudentAcademicProgramRepository academicProgramRepository;
    private final StudentProfileRepository studentProfileRepository;

    public CourseService(
            CourseQueryRepository repository,
            SectionQueryRepository sectionRepository,
            SectionSearchQueryRepository sectionSearchRepository,
            StudentAcademicProgramRepository academicProgramRepository,
            StudentProfileRepository studentProfileRepository) {
        this.repository = repository;
        this.sectionRepository = sectionRepository;
        this.sectionSearchRepository = sectionSearchRepository;
        this.academicProgramRepository = academicProgramRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    public AcademicPageResponse<CourseSummaryResponse> searchCourses(
            String semesterId,
            String query,
            List<String> categories,
            List<String> academicUnitCodes,
            List<String> collegeCodes,
            String professor,
            BigDecimal credits,
            String day,
            String sort,
            int page,
            int size) {
        String normalizedSemesterId = requireSemester(semesterId);
        PageSpec pageSpec = PageSpec.of(page, size);
        CourseSearchCondition condition = new CourseSearchCondition(
                normalizedSemesterId,
                TextQuery.optional(query),
                normalizeValues(categories, this::normalizeCategory),
                normalizeValues(academicUnitCodes, TextQuery::optional),
                normalizeValues(collegeCodes, TextQuery::optional),
                TextQuery.optional(professor),
                validateCredits(credits),
                parseDay(day));
        CourseSort courseSort = CourseSort.parse(sort);
        return AcademicPageResponse.of(
                repository.findCourses(condition, courseSort, pageSpec),
                pageSpec,
                repository.countCourses(condition));
    }

    public CourseDetailResponse getCourse(String semesterId, String courseCode) {
        String normalizedSemesterId = requireSemester(semesterId);
        String normalizedCourseCode = TextQuery.required(courseCode, "과목 코드");
        return repository.findCourse(normalizedSemesterId, normalizedCourseCode)
                .orElseThrow(() -> courseNotFound(
                        normalizedSemesterId, normalizedCourseCode));
    }

    public List<SectionSummaryResponse> getSections(
            String semesterId, String courseCode) {
        CourseDetailResponse course = getCourse(semesterId, courseCode);
        return sectionRepository.findAll(course.semesterId(), course.courseCode());
    }

    public AcademicPageResponse<SectionSearchResponse> searchSections(
            UUID userId,
            String semesterId,
            String query,
            List<String> categories,
            List<String> academicUnitCodes,
            List<String> collegeCodes,
            List<String> completionCategories,
            List<String> targetGrades,
            List<String> preferredAcademicUnitCodes,
            String professor,
            BigDecimal credits,
            String day,
            String sort,
            int page,
            int size) {
        String normalizedSemesterId = requireSemester(semesterId);
        PageSpec pageSpec = PageSpec.of(page, size);
        SectionSearchCondition condition = new SectionSearchCondition(
                normalizedSemesterId,
                TextQuery.optional(query),
                normalizeValues(categories, this::normalizeCategory),
                normalizeValues(academicUnitCodes, TextQuery::optional),
                normalizeValues(collegeCodes, TextQuery::optional),
                normalizeValues(completionCategories, this::normalizeCompletionCategory),
                normalizeValues(targetGrades, this::parseTargetGrade),
                resolvePreferredAcademicUnitCodes(userId, preferredAcademicUnitCodes),
                TextQuery.optional(professor),
                validateCredits(credits),
                parseDay(day));
        CourseSort courseSort = CourseSort.parse(sort);
        return AcademicPageResponse.of(
                sectionSearchRepository.findSections(
                        condition, courseSort, pageSpec),
                pageSpec,
                sectionSearchRepository.countSections(condition));
    }

    public SectionDetailResponse getSection(
            String semesterId, String courseCode, String sectionCode) {
        String normalizedSemesterId = requireSemester(semesterId);
        String normalizedCourseCode = TextQuery.required(courseCode, "과목 코드");
        String normalizedSectionCode = TextQuery.required(sectionCode, "분반 코드");
        return sectionRepository.findById(
                        normalizedSemesterId, normalizedCourseCode, normalizedSectionCode)
                .orElseThrow(() -> new AcademicResourceNotFoundException(
                        "분반을 찾을 수 없습니다. key="
                                + normalizedSemesterId + ":"
                                + normalizedCourseCode + ":"
                                + normalizedSectionCode));
    }

    private String requireSemester(String semesterId) {
        String normalizedSemesterId = TextQuery.required(semesterId, "학기 ID");
        if (!repository.semesterExists(normalizedSemesterId)) {
            throw new AcademicResourceNotFoundException(
                    "학기를 찾을 수 없습니다. semesterId=" + normalizedSemesterId);
        }
        return normalizedSemesterId;
    }

    private BigDecimal validateCredits(BigDecimal credits) {
        if (credits != null && credits.signum() < 0) {
            throw new InvalidAcademicQueryException("학점 필터는 0 이상이어야 합니다.");
        }
        return credits;
    }

    private String parseDay(String day) {
        String value = TextQuery.optional(day);
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "월", "MONDAY" -> "월";
            case "화", "TUESDAY" -> "화";
            case "수", "WEDNESDAY" -> "수";
            case "목", "THURSDAY" -> "목";
            case "금", "FRIDAY" -> "금";
            case "토", "SATURDAY" -> "토";
            case "일", "SUNDAY" -> "일";
            default -> throw new InvalidAcademicQueryException(
                    "day는 요일 한글 한 글자 또는 MONDAY~SUNDAY 형식이어야 합니다.");
        };
    }

    private String parseTargetGrade(String targetGrade) {
        String value = TextQuery.optional(targetGrade);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "1", "1학년" -> "1학년";
            case "2", "2학년" -> "2학년";
            case "3", "3학년" -> "3학년";
            case "4", "4학년" -> "4학년";
            default -> throw new InvalidAcademicQueryException(
                    "targetGrade는 1~4 또는 1학년~4학년 형식이어야 합니다.");
        };
    }

    private String normalizeCategory(String category) {
        String value = TextQuery.optional(category);
        if (value == null) {
            return null;
        }
        String compact = value.replace(" ", "");
        if (compact.equalsIgnoreCase("디지털리터러시")
                || compact.equalsIgnoreCase("AI디지털리터러시")
                || compact.equalsIgnoreCase("AI·디지털리터러시")) {
            return "교양선택(제6영역:AI·디지털리터러시)";
        }
        return value;
    }

    private String normalizeCompletionCategory(String category) {
        String value = TextQuery.optional(category);
        if (value == null) {
            return null;
        }
        return switch (value.replace(" ", "")) {
            case "전공필수" -> "전필";
            case "전공선택" -> "전선";
            case "전공기초" -> "전기";
            case "교양필수" -> "교필";
            case "교양선택" -> "교선";
            case "일반선택" -> "일선";
            default -> value;
        };
    }

    private List<String> resolvePreferredAcademicUnitCodes(
            UUID userId, List<String> requestedCodes) {
        List<String> normalized = normalizeValues(requestedCodes, TextQuery::optional);
        if (!normalized.isEmpty() || userId == null) {
            return normalized;
        }
        List<String> savedPrograms = academicProgramRepository.findActiveByUserId(userId).stream()
                .map(program -> program.academicUnitCode())
                .distinct()
                .toList();
        if (!savedPrograms.isEmpty()) {
            return savedPrograms;
        }
        return studentProfileRepository.findById(userId)
                .map(profile -> profile.academicUnitCode() == null
                        ? List.<String>of()
                        : List.of(profile.academicUnitCode()))
                .orElseGet(List::of);
    }

    private List<String> normalizeValues(
            List<String> values, Function<String, String> normalizer) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(normalizer)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private AcademicResourceNotFoundException courseNotFound(
            String semesterId, String courseCode) {
        return new AcademicResourceNotFoundException(
                "강의를 찾을 수 없습니다. key=" + semesterId + ":" + courseCode);
    }
}
