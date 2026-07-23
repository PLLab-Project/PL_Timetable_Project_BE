package com.example.pl_timetable_project.timetable.service;

import com.example.pl_timetable_project.academic.section.AcademicSection;
import com.example.pl_timetable_project.academic.section.AcademicSectionQueryRepository;
import com.example.pl_timetable_project.academic.section.SectionReference;
import com.example.pl_timetable_project.exception.ForbiddenException;
import com.example.pl_timetable_project.exception.InvalidTimetableException;
import com.example.pl_timetable_project.exception.SectionConflictException;
import com.example.pl_timetable_project.exception.TimetableNotFoundException;
import com.example.pl_timetable_project.exception.UnauthorizedException;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCourseRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCreateRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableUpdateRequest;
import com.example.pl_timetable_project.timetable.dto.response.FreeTimeResponse;
import com.example.pl_timetable_project.timetable.dto.response.TimetableCourseResponse;
import com.example.pl_timetable_project.timetable.dto.response.TimetableResponse;
import com.example.pl_timetable_project.timetable.dto.response.TimetableSummaryResponse;
import com.example.pl_timetable_project.timetable.entity.Timetable;
import com.example.pl_timetable_project.timetable.entity.TimetableCourse;
import com.example.pl_timetable_project.timetable.entity.TimetableMeeting;
import com.example.pl_timetable_project.timetable.repository.TimetableRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final AcademicSectionQueryRepository sectionQueryRepository;

    public TimetableService(
            TimetableRepository timetableRepository,
            AcademicSectionQueryRepository sectionQueryRepository) {
        this.timetableRepository = timetableRepository;
        this.sectionQueryRepository = sectionQueryRepository;
    }

    @Transactional
    public TimetableResponse createTimetable(UUID userId, TimetableCreateRequest request) {
        validateUserId(userId);
        Timetable timetable = new Timetable(userId, request.getSemesterId(), request.getName().trim());
        buildConflictFreeCourses(request.getSemesterId(), request.getSections())
                .forEach(timetable::addCourse);
        return toResponse(timetableRepository.save(timetable));
    }

    public List<TimetableSummaryResponse> getTimetables(UUID userId) {
        validateUserId(userId);
        return timetableRepository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(timetable -> TimetableSummaryResponse.of(
                        timetable, calculateTotalCredits(timetable.getTimetableCourses())))
                .toList();
    }

    public TimetableResponse getTimetable(UUID userId, Long timetableId) {
        return toResponse(getOwnedTimetable(userId, timetableId));
    }

    @Transactional
    public TimetableResponse updateTimetable(
            UUID userId, Long timetableId, TimetableUpdateRequest request) {
        Timetable timetable = getOwnedTimetable(userId, timetableId);
        if (request.getName() != null) {
            timetable.rename(request.getName().trim());
        }
        return toResponse(timetable);
    }

    @Transactional
    public TimetableResponse updateSections(
            UUID userId, Long timetableId, List<TimetableCourseRequest> sections) {
        Timetable timetable = getOwnedTimetable(userId, timetableId);
        List<TimetableCourse> courses = buildConflictFreeCourses(timetable.getSemesterId(), sections);
        timetable.clearCourses();
        courses.forEach(timetable::addCourse);
        return toResponse(timetable);
    }

    @Transactional
    public TimetableResponse addCourse(
            UUID userId, Long timetableId, TimetableCourseRequest request) {
        Timetable timetable = getOwnedTimetable(userId, timetableId);
        TimetableCourse newCourse = resolveCourses(
                timetable.getSemesterId(), List.of(request)).get(0);
        timetable.getTimetableCourses().forEach(existing -> validateNoConflict(existing, newCourse));
        timetable.addCourse(newCourse);
        return toResponse(timetable);
    }

    @Transactional
    public TimetableResponse removeCourse(
            UUID userId, Long timetableId, Long timetableCourseId) {
        Timetable timetable = getOwnedTimetable(userId, timetableId);
        TimetableCourse target = timetable.getTimetableCourses().stream()
                .filter(course -> course.getId().equals(timetableCourseId))
                .findFirst()
                .orElseThrow(() -> new InvalidTimetableException(
                        "시간표에 존재하지 않는 분반입니다. id=" + timetableCourseId));
        timetable.removeCourse(target);
        return toResponse(timetable);
    }

    @Transactional
    public void deleteTimetable(UUID userId, Long timetableId) {
        timetableRepository.delete(getOwnedTimetable(userId, timetableId));
    }

    private Timetable getOwnedTimetable(UUID userId, Long timetableId) {
        validateUserId(userId);
        Timetable timetable = timetableRepository.findByIdWithCourses(timetableId)
                .orElseThrow(() -> new TimetableNotFoundException(timetableId));
        if (!timetable.getUserId().equals(userId)) {
            throw new ForbiddenException("해당 시간표에 접근할 권한이 없습니다. id=" + timetableId);
        }
        return timetable;
    }

    private List<TimetableCourse> buildConflictFreeCourses(
            String semesterId, List<TimetableCourseRequest> requests) {
        List<TimetableCourse> courses = resolveCourses(semesterId, requests);
        for (int i = 0; i < courses.size(); i++) {
            for (int j = i + 1; j < courses.size(); j++) {
                validateNoConflict(courses.get(i), courses.get(j));
            }
        }
        return courses;
    }

    private List<TimetableCourse> resolveCourses(
            String semesterId, List<TimetableCourseRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }

        Map<SectionReference, AcademicSection> catalog =
                sectionQueryRepository.findBySemesterId(semesterId);
        Set<SectionReference> seen = new HashSet<>();
        List<TimetableCourse> courses = new ArrayList<>();

        for (TimetableCourseRequest request : requests) {
            SectionReference reference = new SectionReference(
                    semesterId, request.getCourseCode(), request.getSectionCode());
            if (!seen.add(reference)) {
                throw new InvalidTimetableException(
                        "같은 분반을 중복으로 추가할 수 없습니다: " + reference.displayKey());
            }
            AcademicSection section = catalog.get(reference);
            if (section == null) {
                throw new InvalidTimetableException(
                        "학사 DB에 존재하지 않는 분반입니다: " + reference.displayKey());
            }
            courses.add(new TimetableCourse(section));
        }
        return courses;
    }

    private void validateNoConflict(TimetableCourse left, TimetableCourse right) {
        if (left.conflictsWith(right)) {
            throw new SectionConflictException(String.format(
                    "같은 과목의 다른 분반이거나 수업 시간이 겹칩니다: '%s'(%s) vs '%s'(%s)",
                    left.getCourseName(), left.getSection().displayKey(),
                    right.getCourseName(), right.getSection().displayKey()));
        }
    }

    private BigDecimal calculateTotalCredits(List<TimetableCourse> courses) {
        return courses.stream()
                .map(TimetableCourse::getCredits)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<FreeTimeResponse> calculateFreeTimes(List<TimetableCourse> courses) {
        Map<DayOfWeek, List<MeetingSlot>> byDay = courses.stream()
                .flatMap(course -> course.getMeetings().stream()
                        .map(meeting -> new MeetingSlot(
                                meeting.getDayOfWeek(), meeting.getStartTime(), meeting.getEndTime())))
                .collect(Collectors.groupingBy(
                        MeetingSlot::dayOfWeek, LinkedHashMap::new, Collectors.toList()));

        List<FreeTimeResponse> freeTimes = new ArrayList<>();
        for (Map.Entry<DayOfWeek, List<MeetingSlot>> entry : byDay.entrySet()) {
            List<MeetingSlot> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(MeetingSlot::startTime))
                    .toList();
            for (int i = 0; i < sorted.size() - 1; i++) {
                MeetingSlot current = sorted.get(i);
                MeetingSlot next = sorted.get(i + 1);
                if (current.endTime().isBefore(next.startTime())) {
                    freeTimes.add(new FreeTimeResponse(
                            entry.getKey(), current.endTime(), next.startTime()));
                }
            }
        }
        return freeTimes;
    }

    private TimetableResponse toResponse(Timetable timetable) {
        List<TimetableCourse> courses = timetable.getTimetableCourses();
        return TimetableResponse.of(
                timetable,
                calculateTotalCredits(courses),
                courses.stream().map(TimetableCourseResponse::from).toList(),
                calculateFreeTimes(courses));
    }

    private void validateUserId(UUID userId) {
        if (userId == null) {
            throw new UnauthorizedException("userId 는 필수입니다.");
        }
    }

    private record MeetingSlot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }
}
