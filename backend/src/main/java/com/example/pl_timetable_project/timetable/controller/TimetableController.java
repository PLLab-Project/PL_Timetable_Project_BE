package com.example.pl_timetable_project.timetable.controller;

import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCourseRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCreateRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableSectionsUpdateRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableUpdateRequest;
import com.example.pl_timetable_project.timetable.dto.response.TimetableResponse;
import com.example.pl_timetable_project.timetable.dto.response.TimetableSummaryResponse;
import com.example.pl_timetable_project.timetable.service.TimetableService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timetables")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @PostMapping
    public ResponseEntity<TimetableResponse> createTimetable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TimetableCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(timetableService.createTimetable(principal.userId(), request));
    }

    @GetMapping
    public ResponseEntity<List<TimetableSummaryResponse>> getTimetables(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(timetableService.getTimetables(principal.userId()));
    }

    @GetMapping("/{timetableId}")
    public ResponseEntity<TimetableResponse> getTimetable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId) {
        return ResponseEntity.ok(timetableService.getTimetable(principal.userId(), timetableId));
    }

    @PatchMapping("/{timetableId}")
    public ResponseEntity<TimetableResponse> updateTimetable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableUpdateRequest request) {
        return ResponseEntity.ok(
                timetableService.updateTimetable(principal.userId(), timetableId, request));
    }

    @PatchMapping("/{timetableId}/sections")
    public ResponseEntity<TimetableResponse> updateSections(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableSectionsUpdateRequest request) {
        return ResponseEntity.ok(
                timetableService.updateSections(
                        principal.userId(), timetableId, request.getSections()));
    }

    @PostMapping("/{timetableId}/sections")
    public ResponseEntity<TimetableResponse> addSection(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(timetableService.addCourse(principal.userId(), timetableId, request));
    }

    @DeleteMapping("/{timetableId}/sections/{timetableCourseId}")
    public ResponseEntity<TimetableResponse> removeSection(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId,
            @PathVariable Long timetableCourseId) {
        return ResponseEntity.ok(
                timetableService.removeCourse(
                        principal.userId(), timetableId, timetableCourseId));
    }

    @DeleteMapping("/{timetableId}")
    public ResponseEntity<Void> deleteTimetable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId) {
        timetableService.deleteTimetable(principal.userId(), timetableId);
        return ResponseEntity.noContent().build();
    }
}
