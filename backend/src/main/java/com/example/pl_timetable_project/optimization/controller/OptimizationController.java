package com.example.pl_timetable_project.optimization.controller;

import com.example.pl_timetable_project.optimization.dto.request.OptimizationCreateRequest;
import com.example.pl_timetable_project.optimization.dto.response.OptimizationJobResponse;
import com.example.pl_timetable_project.optimization.service.OptimizationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/optimizations")
public class OptimizationController {

    private final OptimizationService optimizationService;

    public OptimizationController(OptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    @PostMapping
    public ResponseEntity<OptimizationJobResponse> createJob(
            @RequestParam UUID userId,
            @Valid @RequestBody OptimizationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(optimizationService.createJob(userId, request));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<OptimizationJobResponse> getJob(
            @RequestParam UUID userId,
            @PathVariable Long jobId) {
        return ResponseEntity.ok(optimizationService.getJob(userId, jobId));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> cancelJob(
            @RequestParam UUID userId,
            @PathVariable Long jobId) {
        optimizationService.cancelJob(userId, jobId);
        return ResponseEntity.noContent().build();
    }
}
