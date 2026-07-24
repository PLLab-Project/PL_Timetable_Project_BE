package com.example.pl_timetable_project.optimization.entity;

public enum OptimizationJobStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED;

    public boolean isFinished() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT || this == CANCELLED;
    }
}
