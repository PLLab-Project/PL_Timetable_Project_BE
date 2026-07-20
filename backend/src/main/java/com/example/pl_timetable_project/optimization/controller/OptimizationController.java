package com.example.pl_timetable_project.optimization.controller;

import com.example.pl_timetable_project.optimization.dto.request.OptimizationCreateRequest;
import com.example.pl_timetable_project.optimization.dto.response.OptimizationJobResponse;
import com.example.pl_timetable_project.optimization.service.OptimizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * userId 는 인증/User 도메인이 아직 구현되지 않아 임시로 요청 파라미터로 받는다.
 */
@RestController
@RequestMapping("/api/v1/optimizations")
@RequiredArgsConstructor
public class OptimizationController {

    private final OptimizationService optimizationService;

    @PostMapping
    public ResponseEntity<OptimizationJobResponse> createJob(
            @RequestParam Long userId,
            @Valid @RequestBody OptimizationCreateRequest request) {
        OptimizationJobResponse response = optimizationService.createJob(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<OptimizationJobResponse> getJob(
            @RequestParam Long userId,
            @PathVariable Long jobId) {
        return ResponseEntity.ok(optimizationService.getJob(userId, jobId));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> cancelJob(
            @RequestParam Long userId,
            @PathVariable Long jobId) {
        optimizationService.cancelJob(userId, jobId);
        return ResponseEntity.noContent().build();
    }
}
