package com.example.pl_timetable_project.exception;

public class OptimizationAlreadyFinishedException extends ApplicationException {

    public OptimizationAlreadyFinishedException(Long jobId) {
        super(ErrorCode.OPTIMIZATION_ALREADY_FINISHED, "이미 종료된 작업입니다. id=" + jobId);
    }
}
