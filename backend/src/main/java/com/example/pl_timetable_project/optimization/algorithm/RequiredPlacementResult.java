package com.example.pl_timetable_project.optimization.algorithm;

import java.util.List;

public record RequiredPlacementResult(List<CandidateCourse> requiredCourses, List<CandidateCourse> optionalCandidates) {
}
