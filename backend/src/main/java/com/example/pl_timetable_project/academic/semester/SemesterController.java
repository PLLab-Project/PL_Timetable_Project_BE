package com.example.pl_timetable_project.academic.semester;

import com.example.pl_timetable_project.academic.semester.dto.SemesterDataVersionResponse;
import com.example.pl_timetable_project.academic.semester.dto.SemesterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/semesters")
@Tag(name = "학기", description = "학기와 학사 데이터 버전 조회")
public class SemesterController {

    private final SemesterService semesterService;

    public SemesterController(SemesterService semesterService) {
        this.semesterService = semesterService;
    }

    @Operation(summary = "학기 목록 조회")
    @GetMapping
    public ResponseEntity<List<SemesterResponse>> getSemesters(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(semesterService.getSemesters(activeOnly));
    }

    @Operation(summary = "학기 상세 조회")
    @GetMapping("/{semesterId}")
    public ResponseEntity<SemesterResponse> getSemester(
            @PathVariable String semesterId) {
        return ResponseEntity.ok(semesterService.getSemester(semesterId));
    }

    @Operation(summary = "학기 데이터 버전 조회")
    @GetMapping("/{semesterId}/version")
    public ResponseEntity<SemesterDataVersionResponse> getDataVersion(
            @PathVariable String semesterId) {
        return ResponseEntity.ok(semesterService.getDataVersion(semesterId));
    }
}
