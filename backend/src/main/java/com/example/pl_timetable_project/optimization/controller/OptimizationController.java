package com.example.pl_timetable_project.optimization.controller;

import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import com.example.pl_timetable_project.optimization.dto.request.OptimizationCreateRequest;
import com.example.pl_timetable_project.optimization.dto.response.OptimizationJobResponse;
import com.example.pl_timetable_project.optimization.service.OptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/optimizations")
@Tag(name = "자동편성", description = "조건 기반 시간표 자동 편성 작업")
public class OptimizationController {

    private final OptimizationService optimizationService;

    public OptimizationController(OptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    @Operation(summary = "자동편성 작업 생성")
    @PostMapping
    public ResponseEntity<OptimizationJobResponse> createJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody OptimizationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(optimizationService.createJob(principal.userId(), request));
    }

    @Operation(summary = "자동편성 작업 상태·결과 조회")
    @GetMapping("/{jobId}")
    public ResponseEntity<OptimizationJobResponse> getJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long jobId) {
        return ResponseEntity.ok(optimizationService.getJob(principal.userId(), jobId));
    }

    @Operation(summary = "진행 중 자동편성 작업 취소")
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> cancelJob(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long jobId) {
        optimizationService.cancelJob(principal.userId(), jobId);
        return ResponseEntity.noContent().build();
    }
}
