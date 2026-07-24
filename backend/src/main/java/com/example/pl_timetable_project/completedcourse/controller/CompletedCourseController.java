package com.example.pl_timetable_project.completedcourse.controller;

import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseCreateRequest;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseCreditSummaryResponse;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseResponse;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseUpdateRequest;
import com.example.pl_timetable_project.completedcourse.dto.TimetableImportResponse;
import com.example.pl_timetable_project.completedcourse.service.CompletedCourseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/completed-courses")
public class CompletedCourseController {

    private final CompletedCourseService completedCourseService;

    public CompletedCourseController(CompletedCourseService completedCourseService) {
        this.completedCourseService = completedCourseService;
    }

    @PostMapping
    public ResponseEntity<CompletedCourseResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CompletedCourseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(completedCourseService.create(principal.userId(), request));
    }

    @GetMapping
    public ResponseEntity<List<CompletedCourseResponse>> getAll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) CompletedCourseStatus status,
            @RequestParam(required = false) String semester) {
        return ResponseEntity.ok(
                completedCourseService.getAll(principal.userId(), status, semester));
    }

    @GetMapping("/summary")
    public ResponseEntity<CompletedCourseCreditSummaryResponse> summarize(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(completedCourseService.summarize(principal.userId()));
    }

    @GetMapping("/{completedCourseId}")
    public ResponseEntity<CompletedCourseResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID completedCourseId) {
        return ResponseEntity.ok(
                completedCourseService.get(principal.userId(), completedCourseId));
    }

    @PatchMapping("/{completedCourseId}")
    public ResponseEntity<CompletedCourseResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID completedCourseId,
            @Valid @RequestBody CompletedCourseUpdateRequest request) {
        return ResponseEntity.ok(
                completedCourseService.update(principal.userId(), completedCourseId, request));
    }

    @DeleteMapping("/{completedCourseId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID completedCourseId) {
        completedCourseService.delete(principal.userId(), completedCourseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/imports/timetables/{timetableId}")
    public ResponseEntity<TimetableImportResponse> importTimetable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(completedCourseService.importTimetable(principal.userId(), timetableId));
    }

    @PostMapping("/{completedCourseId}/complete")
    public ResponseEntity<CompletedCourseResponse> complete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID completedCourseId) {
        return ResponseEntity.ok(
                completedCourseService.complete(principal.userId(), completedCourseId));
    }
}
