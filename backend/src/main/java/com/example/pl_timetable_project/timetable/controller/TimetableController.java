package com.example.pl_timetable_project.timetable.controller;

import com.example.pl_timetable_project.timetable.dto.request.TimetableCourseRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCreateRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableSectionsUpdateRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableUpdateRequest;
import com.example.pl_timetable_project.timetable.dto.response.TimetableResponse;
import com.example.pl_timetable_project.timetable.dto.response.TimetableSummaryResponse;
import com.example.pl_timetable_project.timetable.service.TimetableService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 기능이 main에 통합되기 전까지 UUID userId를 임시 요청 파라미터로 받는다.
 */
@RestController
@RequestMapping("/api/v1/timetables")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @PostMapping
    public ResponseEntity<TimetableResponse> createTimetable(
            @RequestParam UUID userId,
            @Valid @RequestBody TimetableCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(timetableService.createTimetable(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<TimetableSummaryResponse>> getTimetables(
            @RequestParam UUID userId) {
        return ResponseEntity.ok(timetableService.getTimetables(userId));
    }

    @GetMapping("/{timetableId}")
    public ResponseEntity<TimetableResponse> getTimetable(
            @RequestParam UUID userId,
            @PathVariable Long timetableId) {
        return ResponseEntity.ok(timetableService.getTimetable(userId, timetableId));
    }

    @PatchMapping("/{timetableId}")
    public ResponseEntity<TimetableResponse> updateTimetable(
            @RequestParam UUID userId,
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableUpdateRequest request) {
        return ResponseEntity.ok(timetableService.updateTimetable(userId, timetableId, request));
    }

    @PatchMapping("/{timetableId}/sections")
    public ResponseEntity<TimetableResponse> updateSections(
            @RequestParam UUID userId,
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableSectionsUpdateRequest request) {
        return ResponseEntity.ok(
                timetableService.updateSections(userId, timetableId, request.getSections()));
    }

    @PostMapping("/{timetableId}/sections")
    public ResponseEntity<TimetableResponse> addSection(
            @RequestParam UUID userId,
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(timetableService.addCourse(userId, timetableId, request));
    }

    @DeleteMapping("/{timetableId}/sections/{timetableCourseId}")
    public ResponseEntity<TimetableResponse> removeSection(
            @RequestParam UUID userId,
            @PathVariable Long timetableId,
            @PathVariable Long timetableCourseId) {
        return ResponseEntity.ok(
                timetableService.removeCourse(userId, timetableId, timetableCourseId));
    }

    @DeleteMapping("/{timetableId}")
    public ResponseEntity<Void> deleteTimetable(
            @RequestParam UUID userId,
            @PathVariable Long timetableId) {
        timetableService.deleteTimetable(userId, timetableId);
        return ResponseEntity.noContent().build();
    }
}
