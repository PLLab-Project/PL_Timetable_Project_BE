package com.example.pl_timetable_project.academic.graduation;

import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Evaluation;
import com.example.pl_timetable_project.academic.graduation.GraduationResponses.Rule;
import com.example.pl_timetable_project.auth.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/graduation")
public class GraduationController {

    private final GraduationService graduationService;

    public GraduationController(GraduationService graduationService) {
        this.graduationService = graduationService;
    }

    @GetMapping("/rules")
    public ResponseEntity<Rule> getRule(
            @RequestParam int admissionYear,
            @RequestParam String academicUnit,
            @RequestParam String studentType,
            @RequestParam String programPath) {
        return ResponseEntity.ok(graduationService.getRule(
                admissionYear, academicUnit, studentType, programPath));
    }

    @GetMapping({"/evaluation", "/me/evaluation"})
    public ResponseEntity<Evaluation> evaluate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String semesterId) {
        return ResponseEntity.ok(
                graduationService.evaluate(principal.userId(), semesterId));
    }
}
