package com.example.pl_timetable_project.completedcourse.service;

import com.example.pl_timetable_project.academic.course.dto.CourseRoomResponse;
import com.example.pl_timetable_project.academic.course.dto.CourseSessionResponse;
import com.example.pl_timetable_project.academic.semester.SemesterQueryRepository;
import com.example.pl_timetable_project.completedcourse.dto.OcrCourseMatchStatus;
import com.example.pl_timetable_project.completedcourse.dto.OcrDocumentType;
import com.example.pl_timetable_project.completedcourse.dto.OcrSectionMatchCandidateResponse;
import com.example.pl_timetable_project.completedcourse.dto.RecognizedCourseMeetingResponse;
import com.example.pl_timetable_project.completedcourse.dto.RecognizedCourseResponse;
import com.example.pl_timetable_project.completedcourse.repository.CompletedCourseOcrMatchRepository;
import com.example.pl_timetable_project.completedcourse.repository.CompletedCourseOcrMatchRepository.SectionCandidate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CompletedCourseSectionMatcher {

    private static final Pattern SEMESTER_PATTERN =
            Pattern.compile("(?<!\\d)(20\\d{2})\\D*([12])(?:\\D|$)");
    private static final double MATCH_THRESHOLD = 0.65;
    private static final double UNIQUE_GAP_THRESHOLD = 0.02;
    private static final int MAX_CANDIDATES = 5;

    private final CompletedCourseOcrMatchRepository matchRepository;
    private final SemesterQueryRepository semesterRepository;

    public CompletedCourseSectionMatcher(
            CompletedCourseOcrMatchRepository matchRepository,
            SemesterQueryRepository semesterRepository) {
        this.matchRepository = matchRepository;
        this.semesterRepository = semesterRepository;
    }

    public MatchingResult match(
            OcrDocumentType documentType,
            String recognizedSemester,
            List<RecognizedCourseResponse> courses) {
        String normalizedDocumentSemester = normalizeSemester(recognizedSemester);
        if (courses.isEmpty()) {
            return new MatchingResult(normalizedDocumentSemester, List.of());
        }

        Set<String> semesterIds = semesterIds(normalizedDocumentSemester, courses);
        Set<String> normalizedNames = new LinkedHashSet<>();
        courses.stream()
                .map(RecognizedCourseResponse::courseName)
                .map(CompletedCourseSectionMatcher::normalizeKey)
                .filter(name -> !name.isEmpty())
                .forEach(normalizedNames::add);
        List<SectionCandidate> catalogCandidates =
                matchRepository.findCandidates(semesterIds, normalizedNames);
        String matchingSemester = normalizedDocumentSemester != null
                ? normalizedDocumentSemester
                : inferTimetableSemester(
                        documentType, courses, catalogCandidates);

        List<RecognizedCourseResponse> matchedCourses = courses.stream()
                .map(course -> matchCourse(
                        matchingSemester, course, catalogCandidates))
                .toList();
        return new MatchingResult(
                resolveSemester(matchingSemester, matchedCourses),
                matchedCourses);
    }

    private String inferTimetableSemester(
            OcrDocumentType documentType,
            List<RecognizedCourseResponse> courses,
            List<SectionCandidate> catalogCandidates) {
        if (documentType != OcrDocumentType.TIMETABLE) {
            return null;
        }

        Map<String, SemesterEvidence> evidenceBySemester = new HashMap<>();
        for (RecognizedCourseResponse course : courses) {
            if (normalizeSemester(course.semester()) != null) {
                continue;
            }
            String normalizedName = normalizeKey(course.courseName());
            Map<String, ScoredCandidate> bestBySemester = new HashMap<>();
            catalogCandidates.stream()
                    .filter(candidate -> normalizeKey(candidate.courseName())
                            .equals(normalizedName))
                    .map(candidate -> score(course, null, candidate))
                    .forEach(scored -> bestBySemester.merge(
                            scored.candidate().semesterId(),
                            scored,
                            (current, replacement) -> replacement.score() > current.score()
                                    ? replacement
                                    : current));
            bestBySemester.forEach((semester, scored) -> {
                if (hasInferenceEvidence(course, scored)) {
                    evidenceBySemester.merge(
                            semester,
                            new SemesterEvidence(1, scored.score()),
                            SemesterEvidence::add);
                }
            });
        }
        List<Map.Entry<String, SemesterEvidence>> ranked =
                evidenceBySemester.entrySet().stream()
                        .sorted(Map.Entry.<String, SemesterEvidence>comparingByValue(
                                        Comparator.comparingInt(SemesterEvidence::courseCount)
                                                .thenComparingDouble(SemesterEvidence::totalScore))
                                .reversed()
                                .thenComparing(Map.Entry::getKey,
                                        Comparator.reverseOrder()))
                        .toList();
        if (ranked.isEmpty() || ranked.get(0).getValue().courseCount() < 2) {
            return null;
        }
        SemesterEvidence top = ranked.get(0).getValue();
        SemesterEvidence runnerUp = ranked.size() > 1
                ? ranked.get(1).getValue()
                : SemesterEvidence.NONE;
        boolean decisive = top.courseCount() > runnerUp.courseCount()
                && top.totalScore() - runnerUp.totalScore() >= 0.25;
        return decisive ? ranked.get(0).getKey() : null;
    }

    private boolean hasInferenceEvidence(
            RecognizedCourseResponse course, ScoredCandidate candidate) {
        boolean directEvidence = candidate.evidence().contains("PROFESSOR")
                || candidate.evidence().stream()
                        .anyMatch(evidence -> evidence.startsWith("MEETINGS_"));
        return directEvidence
                && candidate.score() >= 0.55
                && hasRequiredDiscriminatingEvidence(course, candidate);
    }

    private Set<String> semesterIds(
            String documentSemester, List<RecognizedCourseResponse> courses) {
        Set<String> semesterIds = new LinkedHashSet<>();
        if (documentSemester != null) {
            semesterIds.add(documentSemester);
        }

        boolean needsAllSemesters = false;
        for (RecognizedCourseResponse course : courses) {
            String courseSemester = normalizeSemester(course.semester());
            if (courseSemester == null) {
                needsAllSemesters = documentSemester == null;
            } else {
                semesterIds.add(courseSemester);
            }
        }
        if (needsAllSemesters) {
            semesterRepository.findAll(false).stream()
                    .map(semester -> semester.id())
                    .forEach(semesterIds::add);
        }
        return semesterIds;
    }

    private RecognizedCourseResponse matchCourse(
            String documentSemester,
            RecognizedCourseResponse course,
            List<SectionCandidate> catalogCandidates) {
        String normalizedName = normalizeKey(course.courseName());
        String courseSemester = normalizeSemester(course.semester());
        String effectiveSemester = courseSemester != null
                ? courseSemester
                : documentSemester;
        List<ScoredCandidate> scored = catalogCandidates.stream()
                .filter(candidate -> normalizeKey(candidate.courseName())
                        .equals(normalizedName))
                .filter(candidate -> effectiveSemester == null
                        || candidate.semesterId().equals(effectiveSemester))
                .map(candidate -> score(course, effectiveSemester, candidate))
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparing(scoredCandidate ->
                                scoredCandidate.candidate().semesterId(),
                                Comparator.reverseOrder())
                        .thenComparing(scoredCandidate ->
                                scoredCandidate.candidate().courseCode())
                        .thenComparing(scoredCandidate ->
                                scoredCandidate.candidate().sectionCode()))
                .toList();
        if (scored.isEmpty()) {
            return course.withMatching(
                    OcrCourseMatchStatus.UNMATCHED, null, List.of());
        }

        List<OcrSectionMatchCandidateResponse> responses = scored.stream()
                .limit(MAX_CANDIDATES)
                .map(ScoredCandidate::toResponse)
                .toList();
        ScoredCandidate top = scored.get(0);
        double runnerUpScore = scored.size() > 1 ? scored.get(1).score() : -1;
        boolean exactSection = top.score() >= MATCH_THRESHOLD
                && hasRequiredDiscriminatingEvidence(course, top)
                && (scored.size() == 1
                        || top.score() - runnerUpScore >= UNIQUE_GAP_THRESHOLD);
        if (exactSection) {
            OcrSectionMatchCandidateResponse matched = responses.get(0);
            return course.withMatching(
                    OcrCourseMatchStatus.MATCHED, matched, responses);
        }

        long courseIdentities = scored.stream()
                .map(scoredCandidate -> scoredCandidate.candidate().semesterId()
                        + "\u0000"
                        + scoredCandidate.candidate().courseCode())
                .distinct()
                .count();
        if (courseIdentities == 1) {
            return course.withMatching(
                    OcrCourseMatchStatus.COURSE_MATCHED, null, responses);
        }
        return course.withMatching(
                OcrCourseMatchStatus.AMBIGUOUS, null, responses);
    }

    private boolean hasRequiredDiscriminatingEvidence(
            RecognizedCourseResponse course, ScoredCandidate candidate) {
        if (course.professor() != null
                && !course.professor().isBlank()
                && !candidate.evidence().contains("PROFESSOR")) {
            return false;
        }
        boolean recognizedRoom = course.meetings() != null
                && course.meetings().stream()
                        .map(RecognizedCourseMeetingResponse::room)
                        .anyMatch(room -> room != null && !room.isBlank());
        if (recognizedRoom) {
            long recognizedRoomCount = course.meetings().stream()
                    .map(RecognizedCourseMeetingResponse::room)
                    .filter(room -> room != null && !room.isBlank())
                    .count();
            boolean allRoomsMatched = candidate.evidence().stream()
                    .anyMatch(evidence -> evidence.startsWith(
                            "ROOMS_" + recognizedRoomCount + "_OF_"));
            if (!allRoomsMatched) {
                return false;
            }
        }
        if (course.meetings() == null || course.meetings().isEmpty()) {
            return true;
        }
        int meetingCount = course.meetings().size();
        return candidate.evidence().contains(
                "MEETINGS_" + meetingCount + "_OF_" + meetingCount);
    }

    private ScoredCandidate score(
            RecognizedCourseResponse course,
            String effectiveSemester,
            SectionCandidate candidate) {
        double score = 0.35;
        List<String> evidence = new ArrayList<>();
        evidence.add("COURSE_NAME");

        if (effectiveSemester != null
                && effectiveSemester.equals(candidate.semesterId())) {
            score += 0.10;
            evidence.add("SEMESTER");
        }
        if (sameText(course.professor(), candidate.professor())) {
            score += 0.15;
            evidence.add("PROFESSOR");
        }
        if (course.credits() != null
                && candidate.credits() != null
                && course.credits().compareTo(candidate.credits()) == 0) {
            score += 0.05;
            evidence.add("CREDITS");
        }

        MeetingScore meetingScore = meetingScore(
                course.meetings(), candidate.sessions());
        if (meetingScore.similarity() > 0) {
            score += 0.35 * meetingScore.similarity();
            evidence.add("MEETINGS_" + meetingScore.matchedMeetings()
                    + "_OF_" + course.meetings().size());
            if (meetingScore.matchedRooms() > 0) {
                evidence.add("ROOMS_" + meetingScore.matchedRooms()
                        + "_OF_" + course.meetings().size());
            }
        }
        return new ScoredCandidate(
                candidate, Math.min(1.0, score), List.copyOf(evidence));
    }

    private MeetingScore meetingScore(
            List<RecognizedCourseMeetingResponse> recognizedMeetings,
            List<CourseSessionResponse> sessions) {
        if (recognizedMeetings == null
                || recognizedMeetings.isEmpty()
                || sessions == null
                || sessions.isEmpty()) {
            return MeetingScore.NONE;
        }

        List<CourseSessionResponse> availableSessions = new ArrayList<>(sessions);
        double total = 0;
        int matchedMeetings = 0;
        int matchedRooms = 0;
        for (RecognizedCourseMeetingResponse recognized : recognizedMeetings) {
            SessionScore best = null;
            for (CourseSessionResponse session : availableSessions) {
                SessionScore current = sessionScore(recognized, session);
                if (best == null || current.similarity() > best.similarity()) {
                    best = current;
                }
            }
            if (best != null && best.similarity() > 0) {
                total += best.similarity();
                matchedMeetings++;
                if (best.roomMatched()) {
                    matchedRooms++;
                }
                availableSessions.remove(best.session());
            }
        }
        return new MeetingScore(
                total / recognizedMeetings.size(),
                matchedMeetings,
                matchedRooms);
    }

    private SessionScore sessionScore(
            RecognizedCourseMeetingResponse recognized,
            CourseSessionResponse session) {
        if (recognized.dayOfWeek() != null
                && recognized.dayOfWeek() != session.dayOfWeek()) {
            return new SessionScore(session, 0, false);
        }

        double earned = 0;
        double possible = 0;
        if (recognized.dayOfWeek() != null) {
            possible += 0.35;
            earned += 0.35;
        }
        if (recognized.startTime() != null) {
            possible += 0.20;
            double similarity = timeSimilarity(
                    recognized.startTime(), session.startTime());
            if (similarity == 0) {
                return new SessionScore(session, 0, false);
            }
            earned += 0.20 * similarity;
        }
        if (recognized.endTime() != null) {
            possible += 0.20;
            double similarity = timeSimilarity(
                    recognized.endTime(), session.endTime());
            if (similarity == 0) {
                return new SessionScore(session, 0, false);
            }
            earned += 0.20 * similarity;
        }

        boolean roomMatched = roomMatches(recognized.room(), session);
        if (recognized.room() != null && !recognized.room().isBlank()) {
            possible += 0.25;
            if (roomMatched) {
                earned += 0.25;
            }
        }
        return new SessionScore(
                session, possible == 0 ? 0 : earned / possible, roomMatched);
    }

    private double timeSimilarity(LocalTime recognized, LocalTime actual) {
        long difference = Math.abs(Duration.between(recognized, actual).toMinutes());
        if (difference <= 5) {
            return 1.0;
        }
        if (difference <= 15) {
            return 0.75;
        }
        if (difference <= 30) {
            return 0.25;
        }
        return 0;
    }

    private boolean roomMatches(
            String recognizedRoom, CourseSessionResponse session) {
        String recognized = normalizeKey(recognizedRoom);
        if (recognized.isEmpty()) {
            return false;
        }

        List<String> rooms = new ArrayList<>();
        rooms.add(session.roomCode());
        rooms.add(session.roomLabel());
        rooms.add(session.buildingName());
        if (session.rooms() != null) {
            for (CourseRoomResponse room : session.rooms()) {
                rooms.add(room.roomCode());
                rooms.add(room.roomLabel());
                rooms.add(room.buildingName());
            }
        }
        return rooms.stream()
                .map(CompletedCourseSectionMatcher::normalizeKey)
                .filter(room -> !room.isEmpty())
                .anyMatch(room -> room.contains(recognized)
                        || recognized.contains(room));
    }

    private static boolean sameText(String first, String second) {
        String normalizedFirst = normalizeKey(first);
        return !normalizedFirst.isEmpty()
                && normalizedFirst.equals(normalizeKey(second));
    }

    static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    static String normalizeSemester(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = SEMESTER_PATTERN.matcher(value.strip());
        return matcher.find() ? matcher.group(1) + "-" + matcher.group(2) : null;
    }

    private String resolveSemester(
            String recognizedSemester, List<RecognizedCourseResponse> courses) {
        if (recognizedSemester != null) {
            return recognizedSemester;
        }
        Set<String> matchedSemesters = new LinkedHashSet<>();
        courses.stream()
                .filter(course -> course.matchStatus() == OcrCourseMatchStatus.MATCHED)
                .map(RecognizedCourseResponse::matchedSection)
                .filter(java.util.Objects::nonNull)
                .map(OcrSectionMatchCandidateResponse::semesterId)
                .forEach(matchedSemesters::add);
        return matchedSemesters.size() == 1
                ? matchedSemesters.iterator().next()
                : null;
    }

    public record MatchingResult(
            String resolvedSemester, List<RecognizedCourseResponse> courses) {
    }

    private record ScoredCandidate(
            SectionCandidate candidate, double score, List<String> evidence) {

        private OcrSectionMatchCandidateResponse toResponse() {
            return new OcrSectionMatchCandidateResponse(
                    candidate.semesterId(),
                    candidate.courseCode(),
                    candidate.sectionCode(),
                    candidate.courseName(),
                    candidate.professor(),
                    candidate.category(),
                    candidate.credits(),
                    candidate.rawLectureTime(),
                    candidate.rawLocation(),
                    candidate.sessions(),
                    BigDecimal.valueOf(score)
                            .setScale(2, RoundingMode.HALF_UP),
                    evidence);
        }
    }

    private record MeetingScore(
            double similarity, int matchedMeetings, int matchedRooms) {

        private static final MeetingScore NONE = new MeetingScore(0, 0, 0);
    }

    private record SemesterEvidence(int courseCount, double totalScore) {

        private static final SemesterEvidence NONE = new SemesterEvidence(0, 0);

        private SemesterEvidence add(SemesterEvidence other) {
            return new SemesterEvidence(
                    courseCount + other.courseCount,
                    totalScore + other.totalScore);
        }
    }

    private record SessionScore(
            CourseSessionResponse session, double similarity, boolean roomMatched) {
    }
}
