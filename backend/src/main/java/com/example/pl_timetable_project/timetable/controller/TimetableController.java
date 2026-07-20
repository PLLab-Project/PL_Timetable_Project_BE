package com.example.pl_timetable_project.timetable.controller;

import com.example.pl_timetable_project.timetable.dto.request.TimetableCreateRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableSectionsUpdateRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableUpdateRequest;
import com.example.pl_timetable_project.timetable.dto.response.TimetableResponse;
import com.example.pl_timetable_project.timetable.dto.response.TimetableSummaryResponse;
import com.example.pl_timetable_project.timetable.service.TimetableService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * userId 는 인증/User 도메인이 아직 구현되지 않아 임시로 요청 파라미터로 받는다.
 * 추후 인증이 붙으면 인증 정보에서 추출하는 방식으로 교체될 예정이다.
 */
@RestController
@RequestMapping("/api/v1/timetables")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;

    @PostMapping
    public ResponseEntity<TimetableResponse> createTimetable(
            @RequestParam Long userId,
            @Valid @RequestBody TimetableCreateRequest request) {
        TimetableResponse response = timetableService.createTimetable(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TimetableSummaryResponse>> getTimetables(@RequestParam Long userId) {
        return ResponseEntity.ok(timetableService.getTimetables(userId));
    }

    @GetMapping("/{timetableId}")
    public ResponseEntity<TimetableResponse> getTimetable(
            @RequestParam Long userId,
            @PathVariable Long timetableId) {
        return ResponseEntity.ok(timetableService.getTimetable(userId, timetableId));
    }

    @PatchMapping("/{timetableId}")
    public ResponseEntity<TimetableResponse> updateTimetable(
            @RequestParam Long userId,
            @PathVariable Long timetableId,
            @RequestBody TimetableUpdateRequest request) {
        return ResponseEntity.ok(timetableService.updateTimetable(userId, timetableId, request));
    }

    @PatchMapping("/{timetableId}/sections")
    public ResponseEntity<TimetableResponse> updateSections(
            @RequestParam Long userId,
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableSectionsUpdateRequest request) {
        return ResponseEntity.ok(timetableService.updateSections(userId, timetableId, request.getSections()));
    }

    @DeleteMapping("/{timetableId}")
    public ResponseEntity<Void> deleteTimetable(
            @RequestParam Long userId,
            @PathVariable Long timetableId) {
        timetableService.deleteTimetable(userId, timetableId);
        return ResponseEntity.noContent().build();
    }
}
