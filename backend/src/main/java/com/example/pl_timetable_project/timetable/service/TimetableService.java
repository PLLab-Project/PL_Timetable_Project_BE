package com.example.pl_timetable_project.timetable.service;

import com.example.pl_timetable_project.timetable.entity.Timetable;
import com.example.pl_timetable_project.timetable.entity.TimetableCourse;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCourseRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCreateRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableUpdateRequest;
import com.example.pl_timetable_project.timetable.dto.response.FreeTimeResponse;
import com.example.pl_timetable_project.timetable.dto.response.TimetableCourseResponse;
import com.example.pl_timetable_project.timetable.dto.response.TimetableResponse;
import com.example.pl_timetable_project.timetable.dto.response.TimetableSummaryResponse;
import com.example.pl_timetable_project.exception.ForbiddenException;
import com.example.pl_timetable_project.exception.InvalidTimetableException;
import com.example.pl_timetable_project.exception.SectionConflictException;
import com.example.pl_timetable_project.exception.TimetableNotFoundException;
import com.example.pl_timetable_project.exception.UnauthorizedException;
import com.example.pl_timetable_project.timetable.repository.TimetableRepository;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimetableService {

    private final TimetableRepository timetableRepository;

    @Transactional
    public TimetableResponse createTimetable(Long userId, TimetableCreateRequest request) {
        validateUserId(userId);

        Timetable timetable = new Timetable(userId, request.getName(), request.getYear(), request.getSemester());
        List<TimetableCourse> courses = buildConflictFreeCourses(request.getSections());
        courses.forEach(timetable::addCourse);

        Timetable saved = timetableRepository.save(timetable);
        return toResponse(saved);
    }

    public List<TimetableSummaryResponse> getTimetables(Long userId) {
        validateUserId(userId);

        return timetableRepository.findAllByUserId(userId).stream()
                .map(timetable -> TimetableSummaryResponse.of(timetable, calculateTotalCredits(timetable.getTimetableCourses())))
                .toList();
    }

    public TimetableResponse getTimetable(Long userId, Long timetableId) {
        Timetable timetable = getOwnedTimetable(userId, timetableId);
        return toResponse(timetable);
    }

    @Transactional
    public TimetableResponse updateTimetable(Long userId, Long timetableId, TimetableUpdateRequest request) {
        Timetable timetable = getOwnedTimetable(userId, timetableId);
        timetable.updateInfo(request.getName(), request.getYear(), request.getSemester());
        return toResponse(timetable);
    }

    @Transactional
    public TimetableResponse updateSections(Long userId, Long timetableId, List<TimetableCourseRequest> sections) {
        Timetable timetable = getOwnedTimetable(userId, timetableId);

        List<TimetableCourse> courses = buildConflictFreeCourses(sections);
        timetable.clearCourses();
        courses.forEach(timetable::addCourse);

        return toResponse(timetable);
    }

    @Transactional
    public TimetableResponse addCourse(Long userId, Long timetableId, TimetableCourseRequest request) {
        Timetable timetable = getOwnedTimetable(userId, timetableId);

        TimetableCourse newCourse = toCourseEntity(request);
        for (TimetableCourse existing : timetable.getTimetableCourses()) {
            if (existing.conflictsWith(newCourse)) {
                throw new SectionConflictException(conflictMessage(existing, newCourse));
            }
        }
        timetable.addCourse(newCourse);

        return toResponse(timetable);
    }

    @Transactional
    public TimetableResponse removeCourse(Long userId, Long timetableId, Long timetableCourseId) {
        Timetable timetable = getOwnedTimetable(userId, timetableId);

        TimetableCourse target = timetable.getTimetableCourses().stream()
                .filter(course -> course.getId().equals(timetableCourseId))
                .findFirst()
                .orElseThrow(() -> new InvalidTimetableException("시간표에 존재하지 않는 강의입니다. id=" + timetableCourseId));
        timetable.removeCourse(target);

        return toResponse(timetable);
    }

    @Transactional
    public void deleteTimetable(Long userId, Long timetableId) {
        Timetable timetable = getOwnedTimetable(userId, timetableId);
        timetableRepository.delete(timetable);
    }

    private Timetable getOwnedTimetable(Long userId, Long timetableId) {
        validateUserId(userId);
        Timetable timetable = timetableRepository.findByIdWithCourses(timetableId)
                .orElseThrow(() -> new TimetableNotFoundException(timetableId));
        if (!timetable.getUserId().equals(userId)) {
            throw new ForbiddenException("해당 시간표에 접근할 권한이 없습니다. id=" + timetableId);
        }
        return timetable;
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("userId 는 필수입니다.");
        }
    }

    private List<TimetableCourse> buildConflictFreeCourses(List<TimetableCourseRequest> sections) {
        List<TimetableCourse> courses = new ArrayList<>();
        if (sections == null) {
            return courses;
        }
        for (TimetableCourseRequest request : sections) {
            TimetableCourse newCourse = toCourseEntity(request);
            for (TimetableCourse existing : courses) {
                if (existing.conflictsWith(newCourse)) {
                    throw new SectionConflictException(conflictMessage(existing, newCourse));
                }
            }
            courses.add(newCourse);
        }
        return courses;
    }

    private TimetableCourse toCourseEntity(TimetableCourseRequest request) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new InvalidTimetableException(
                    "강의 시작 시간은 종료 시간보다 빨라야 합니다. courseId=" + request.getCourseId());
        }
        return new TimetableCourse(
                request.getCourseId(),
                request.getCourseName(),
                request.getProfessorName(),
                request.getCredit(),
                request.getDayOfWeek(),
                request.getStartTime(),
                request.getEndTime());
    }

    private String conflictMessage(TimetableCourse a, TimetableCourse b) {
        return String.format("강의 시간이 겹칩니다: '%s'(%s %s~%s) vs '%s'(%s %s~%s)",
                a.getCourseName(), a.getDayOfWeek(), a.getStartTime(), a.getEndTime(),
                b.getCourseName(), b.getDayOfWeek(), b.getStartTime(), b.getEndTime());
    }

    private int calculateTotalCredits(List<TimetableCourse> courses) {
        Map<Long, Integer> creditByCourseId = new HashMap<>();
        for (TimetableCourse course : courses) {
            creditByCourseId.putIfAbsent(course.getCourseId(), course.getCredit());
        }
        return creditByCourseId.values().stream().mapToInt(Integer::intValue).sum();
    }

    private List<FreeTimeResponse> calculateFreeTimes(List<TimetableCourse> courses) {
        Map<DayOfWeek, List<TimetableCourse>> byDay = courses.stream()
                .collect(Collectors.groupingBy(TimetableCourse::getDayOfWeek, LinkedHashMap::new, Collectors.toList()));

        List<FreeTimeResponse> freeTimes = new ArrayList<>();
        for (Map.Entry<DayOfWeek, List<TimetableCourse>> entry : byDay.entrySet()) {
            List<TimetableCourse> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(TimetableCourse::getStartTime))
                    .toList();
            for (int i = 0; i < sorted.size() - 1; i++) {
                TimetableCourse current = sorted.get(i);
                TimetableCourse next = sorted.get(i + 1);
                if (current.getEndTime().isBefore(next.getStartTime())) {
                    freeTimes.add(new FreeTimeResponse(entry.getKey(), current.getEndTime(), next.getStartTime()));
                }
            }
        }
        return freeTimes;
    }

    private TimetableResponse toResponse(Timetable timetable) {
        List<TimetableCourse> courses = timetable.getTimetableCourses();
        List<TimetableCourseResponse> sectionResponses = courses.stream()
                .map(TimetableCourseResponse::from)
                .toList();
        return TimetableResponse.of(
                timetable,
                calculateTotalCredits(courses),
                sectionResponses,
                calculateFreeTimes(courses));
    }
}
