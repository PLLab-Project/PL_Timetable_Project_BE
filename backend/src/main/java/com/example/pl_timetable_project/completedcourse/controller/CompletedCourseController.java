package com.example.pl_timetable_project.completedcourse.controller;

import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import com.example.pl_timetable_project.completedcourse.CompletedCourseStatus;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseCreateRequest;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseCreditSummaryResponse;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseResponse;
import com.example.pl_timetable_project.completedcourse.dto.CompletedCourseUpdateRequest;
import com.example.pl_timetable_project.completedcourse.dto.TimetableImportResponse;
import com.example.pl_timetable_project.completedcourse.service.CompletedCourseService;
import com.example.pl_timetable_project.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/completed-courses")
@Tag(name = "이수과목", description = "내 이수·수강 중 과목과 학점 관리")
public class CompletedCourseController {

    private final CompletedCourseService completedCourseService;

    public CompletedCourseController(CompletedCourseService completedCourseService) {
        this.completedCourseService = completedCourseService;
    }

    @Operation(summary = "이수과목 직접 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompletedCourseResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CompletedCourseCreateRequest request) {
        return ApiResponse.success(completedCourseService.create(
                principal.userId(), request));
    }

    @Operation(summary = "내 이수과목 목록 조회")
    @GetMapping
    public ApiResponse<List<CompletedCourseResponse>> getAll(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) CompletedCourseStatus status,
            @RequestParam(required = false) String semester) {
        return ApiResponse.success(
                completedCourseService.getAll(principal.userId(), status, semester));
    }

    @Operation(summary = "내 이수학점 요약")
    @GetMapping("/summary")
    public ApiResponse<CompletedCourseCreditSummaryResponse> summarize(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(completedCourseService.summarize(principal.userId()));
    }

    @Operation(summary = "이수과목 단건 조회")
    @GetMapping("/{completedCourseId}")
    public ApiResponse<CompletedCourseResponse> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID completedCourseId) {
        return ApiResponse.success(
                completedCourseService.get(principal.userId(), completedCourseId));
    }

    @Operation(summary = "이수과목 수정")
    @PatchMapping("/{completedCourseId}")
    public ApiResponse<CompletedCourseResponse> update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID completedCourseId,
            @Valid @RequestBody CompletedCourseUpdateRequest request) {
        return ApiResponse.success(
                completedCourseService.update(principal.userId(), completedCourseId, request));
    }

    @Operation(summary = "이수과목 삭제")
    @DeleteMapping("/{completedCourseId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID completedCourseId) {
        completedCourseService.delete(principal.userId(), completedCourseId);
        return ApiResponse.success();
    }

    @Operation(summary = "시간표 과목을 이수과목으로 가져오기")
    @PostMapping("/imports/timetables/{timetableId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TimetableImportResponse> importTimetable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long timetableId) {
        return ApiResponse.success(completedCourseService.importTimetable(
                principal.userId(), timetableId));
    }

    @Operation(summary = "수강 중 과목을 이수 완료로 전환")
    @PostMapping("/{completedCourseId}/complete")
    public ApiResponse<CompletedCourseResponse> complete(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID completedCourseId) {
        return ApiResponse.success(
                completedCourseService.complete(principal.userId(), completedCourseId));
    }
}
