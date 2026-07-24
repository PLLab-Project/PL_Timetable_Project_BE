package com.example.pl_timetable_project.academic.semester;

import com.example.pl_timetable_project.academic.semester.dto.SemesterDataVersionResponse;
import com.example.pl_timetable_project.academic.semester.dto.SemesterResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/semesters")
public class SemesterController {

    private final SemesterService semesterService;

    public SemesterController(SemesterService semesterService) {
        this.semesterService = semesterService;
    }

    @GetMapping
    public ResponseEntity<List<SemesterResponse>> getSemesters(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(semesterService.getSemesters(activeOnly));
    }

    @GetMapping("/{semesterId}")
    public ResponseEntity<SemesterResponse> getSemester(
            @PathVariable String semesterId) {
        return ResponseEntity.ok(semesterService.getSemester(semesterId));
    }

    @GetMapping("/{semesterId}/version")
    public ResponseEntity<SemesterDataVersionResponse> getDataVersion(
            @PathVariable String semesterId) {
        return ResponseEntity.ok(semesterService.getDataVersion(semesterId));
    }
}
