package com.example.pl_timetable_project.optimization.algorithm;

public record ScoredCombination(ScheduleCombination combination, double score, int attendanceDays,
                                 int totalFreeMinutes) {
}
