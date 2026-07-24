package com.example.pl_timetable_project.completedcourse.service;

import com.example.pl_timetable_project.common.exception.BusinessException;
import com.example.pl_timetable_project.completedcourse.CompletedCourseErrorCode;
import com.example.pl_timetable_project.completedcourse.CompletedCourseInputSource;
import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseCreateRequest;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseCreditSummaryResponse;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseResponse;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseUpdateRequest;
import com.example.pl_timetable_project.completedcourse.dto.TimetableImportResponse;
import com.example.pl_timetable_project.completedcourse.entity.CompletedCourse;
import com.example.pl_timetable_project.completedcourse.repository.CompletedCourseRepository;
import com.example.pl_timetable_project.completedcourse.repository.CompletedCourseTimetableQueryRepository;
import com.example.pl_timetable_project.completedcourse.repository.CompletedCourseTimetableQueryRepository.TimetableSectionSnapshot;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CompletedCourseService {

    private final CompletedCourseRepository completedCourseRepository;
    private final CompletedCourseTimetableQueryRepository timetableQueryRepository;

    public CompletedCourseService(
            CompletedCourseRepository completedCourseRepository,
            CompletedCourseTimetableQueryRepository timetableQueryRepository) {
        this.completedCourseRepository = completedCourseRepository;
        this.timetableQueryRepository = timetableQueryRepository;
    }

    @Transactional
    public CompletedCourseResponse create(UUID userId, CompletedCourseCreateRequest request) {
        CompletedCourse course = new CompletedCourse(
                requireUserId(userId),
                normalizeOptional(request.courseCode()),
                normalizeRequired(request.courseName()),
                request.credits(),
                normalizeRequired(request.category()),
                normalizeOptional(request.area()),
                normalizeOptional(request.semester()),
                request.status(),
                null,
                null,
                CompletedCourseInputSource.MANUAL,
                null);
        return CompletedCourseResponse.from(completedCourseRepository.save(course));
    }

    public List<CompletedCourseResponse> getAll(
            UUID userId, CompletedCourseStatus status, String semester) {
        String normalizedSemester = normalizeOptional(semester);
        return completedCourseRepository.findAllByUserIdOrderByCreatedAtDesc(requireUserId(userId))
                .stream()
                .filter(course -> status == null || course.getStatus() == status)
                .filter(course -> normalizedSemester == null
                        || normalizedSemester.equals(course.getSemester()))
                .map(CompletedCourseResponse::from)
                .toList();
    }

    public CompletedCourseResponse get(UUID userId, UUID completedCourseId) {
        return CompletedCourseResponse.from(findOwned(userId, completedCourseId));
    }

    @Transactional
    public CompletedCourseResponse update(
            UUID userId, UUID completedCourseId, CompletedCourseUpdateRequest request) {
        CompletedCourse course = findOwned(userId, completedCourseId);
        course.update(
                normalizeOptionalUpdate(request.courseCode()),
                normalizeRequiredUpdate(request.courseName()),
                request.credits(),
                normalizeRequiredUpdate(request.category()),
                normalizeOptionalUpdate(request.area()),
                normalizeOptionalUpdate(request.semester()),
                request.status());
        return CompletedCourseResponse.from(course);
    }

    @Transactional
    public void delete(UUID userId, UUID completedCourseId) {
        completedCourseRepository.delete(findOwned(userId, completedCourseId));
    }

    public CompletedCourseCreditSummaryResponse summarize(UUID userId) {
        List<CompletedCourse> courses =
                completedCourseRepository.findAllByUserIdOrderByCreatedAtDesc(requireUserId(userId));
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> byArea = new LinkedHashMap<>();
        Map<String, BigDecimal> byStatus = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal completed = BigDecimal.ZERO;
        BigDecimal inProgress = BigDecimal.ZERO;

        for (CompletedCourse course : courses) {
            BigDecimal credits = course.getCredits();
            total = total.add(credits);
            byCategory.merge(course.getCategory(), credits, BigDecimal::add);
            if (course.getArea() != null) {
                byArea.merge(course.getArea(), credits, BigDecimal::add);
            }
            byStatus.merge(course.getStatus().name(), credits, BigDecimal::add);
            if (course.getStatus() == CompletedCourseStatus.COMPLETED) {
                completed = completed.add(credits);
            } else if (course.getStatus() == CompletedCourseStatus.IN_PROGRESS) {
                inProgress = inProgress.add(credits);
            }
        }

        return new CompletedCourseCreditSummaryResponse(
                total, completed, inProgress, byCategory, byArea, byStatus);
    }

    @Transactional
    public TimetableImportResponse importTimetable(UUID userId, Long timetableId) {
        UUID ownerId = requireUserId(userId);
        if (timetableId == null
                || !timetableQueryRepository.existsOwnedTimetable(ownerId, timetableId)) {
            throw new BusinessException(CompletedCourseErrorCode.TIMETABLE_NOT_FOUND);
        }

        List<CompletedCourse> imported = new ArrayList<>();
        int skipped = 0;
        for (TimetableSectionSnapshot snapshot :
                timetableQueryRepository.findSections(ownerId, timetableId)) {
            boolean exists = completedCourseRepository
                    .existsByUserIdAndInputSourceAndSemesterAndCourseCodeAndSectionCode(
                            ownerId,
                            CompletedCourseInputSource.TIMETABLE,
                            snapshot.semesterId(),
                            snapshot.courseCode(),
                            snapshot.sectionCode());
            if (exists) {
                skipped++;
                continue;
            }
            imported.add(new CompletedCourse(
                    ownerId,
                    snapshot.courseCode(),
                    snapshot.courseName(),
                    snapshot.credits(),
                    snapshot.category(),
                    null,
                    snapshot.semesterId(),
                    CompletedCourseStatus.IN_PROGRESS,
                    null,
                    snapshot.sectionCode(),
                    CompletedCourseInputSource.TIMETABLE,
                    snapshotMap(snapshot)));
        }

        List<CompletedCourseResponse> records = completedCourseRepository.saveAll(imported).stream()
                .map(CompletedCourseResponse::from)
                .toList();
        return new TimetableImportResponse(timetableId, records.size(), skipped, records);
    }

    @Transactional
    public CompletedCourseResponse complete(UUID userId, UUID completedCourseId) {
        CompletedCourse course = findOwned(userId, completedCourseId);
        if (course.getStatus() != CompletedCourseStatus.IN_PROGRESS) {
            throw new BusinessException(CompletedCourseErrorCode.INVALID_STATUS_TRANSITION);
        }
        course.markCompleted();
        return CompletedCourseResponse.from(course);
    }

    private CompletedCourse findOwned(UUID userId, UUID completedCourseId) {
        if (completedCourseId == null) {
            throw new BusinessException(CompletedCourseErrorCode.NOT_FOUND);
        }
        return completedCourseRepository
                .findByIdAndUserId(completedCourseId, requireUserId(userId))
                .orElseThrow(() -> new BusinessException(CompletedCourseErrorCode.NOT_FOUND));
    }

    private UUID requireUserId(UUID userId) {
        if (userId == null) {
            throw new BusinessException(CompletedCourseErrorCode.NOT_FOUND);
        }
        return userId;
    }

    private Map<String, Object> snapshotMap(TimetableSectionSnapshot snapshot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("timetableId", snapshot.timetableId());
        values.put("timetableCourseId", snapshot.timetableCourseId());
        if (snapshot.professorName() != null) {
            values.put("professorName", snapshot.professorName());
        }
        return values;
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(CompletedCourseErrorCode.INVALID_REQUEST);
        }
        return value.trim();
    }

    private String normalizeRequiredUpdate(String value) {
        return value == null ? null : normalizeRequired(value);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeOptionalUpdate(String value) {
        return value == null ? null : normalizeOptional(value);
    }
}
