package com.example.pl_timetable_project.academic.course;

import com.example.pl_timetable_project.academic.common.AcademicPageResponse;
import com.example.pl_timetable_project.academic.common.PageSpec;
import com.example.pl_timetable_project.academic.common.TextQuery;
import com.example.pl_timetable_project.academic.course.dto.CourseDetailResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseSummaryResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionDetailResponse;
import com.example.pl_timetable_project.academic.course.dto.SectionSummaryResponse;
import com.example.pl_timetable_project.exception.AcademicResourceNotFoundException;
import com.example.pl_timetable_project.exception.InvalidAcademicQueryException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseQueryRepository repository;
    private final SectionQueryRepository sectionRepository;

    public CourseService(
            CourseQueryRepository repository,
            SectionQueryRepository sectionRepository) {
        this.repository = repository;
        this.sectionRepository = sectionRepository;
    }

    public AcademicPageResponse<CourseSummaryResponse> searchCourses(
            String semesterId,
            String query,
            String category,
            String academicUnitCode,
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
                TextQuery.optional(category),
                TextQuery.optional(academicUnitCode),
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

    private AcademicResourceNotFoundException courseNotFound(
            String semesterId, String courseCode) {
        return new AcademicResourceNotFoundException(
                "강의를 찾을 수 없습니다. key=" + semesterId + ":" + courseCode);
    }
}
