package com.example.pl_timetable_project.timetable.controller;

import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import com.example.pl_timetable_project.common.response.ApiResponse;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCourseRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableCreateRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableSectionsUpdateRequest;
import com.example.pl_timetable_project.timetable.dto.request.TimetableUpdateRequest;
import com.example.pl_timetable_project.timetable.dto.response.TimetableResponse;
import com.example.pl_timetable_project.timetable.dto.response.TimetableSummaryResponse;
import com.example.pl_timetable_project.timetable.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/timetables")
@Tag(name = "시간표", description = "내 시간표와 분반 구성 관리")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @Operation(summary = "시간표 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TimetableResponse> createTimetable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody TimetableCreateRequest request) {
        return ApiResponse.success(timetableService.createTimetable(
                principal.userId(), request));
    }

    @Operation(summary = "내 시간표 목록 조회")
    @GetMapping
    public ApiResponse<List<TimetableSummaryResponse>> getTimetables(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(timetableService.getTimetables(principal.userId()));
    }

    @Operation(summary = "시간표 상세 조회")
    @GetMapping("/{timetableId}")
    public ApiResponse<TimetableResponse> getTimetable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId) {
        return ApiResponse.success(
                timetableService.getTimetable(principal.userId(), timetableId));
    }

    @Operation(summary = "시간표 이름 변경")
    @PatchMapping("/{timetableId}")
    public ApiResponse<TimetableResponse> updateTimetable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableUpdateRequest request) {
        return ApiResponse.success(
                timetableService.updateTimetable(principal.userId(), timetableId, request));
    }

    @Operation(summary = "시간표 분반 구성 전체 교체")
    @PatchMapping("/{timetableId}/sections")
    public ApiResponse<TimetableResponse> updateSections(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableSectionsUpdateRequest request) {
        return ApiResponse.success(
                timetableService.updateSections(
                        principal.userId(), timetableId, request.getSections()));
    }

    @Operation(summary = "시간표에 분반 추가")
    @PostMapping("/{timetableId}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TimetableResponse> addSection(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId,
            @Valid @RequestBody TimetableCourseRequest request) {
        return ApiResponse.success(timetableService.addCourse(
                principal.userId(), timetableId, request));
    }

    @Operation(summary = "시간표에서 분반 삭제")
    @DeleteMapping("/{timetableId}/sections/{timetableCourseId}")
    public ApiResponse<TimetableResponse> removeSection(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId,
            @PathVariable Long timetableCourseId) {
        return ApiResponse.success(
                timetableService.removeCourse(
                        principal.userId(), timetableId, timetableCourseId));
    }

    @Operation(summary = "시간표 삭제")
    @DeleteMapping("/{timetableId}")
    public ApiResponse<Void> deleteTimetable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId) {
        timetableService.deleteTimetable(principal.userId(), timetableId);
        return ApiResponse.success();
    }
}
