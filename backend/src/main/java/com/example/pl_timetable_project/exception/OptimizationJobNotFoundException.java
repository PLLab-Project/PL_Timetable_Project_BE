package com.example.pl_timetable_project.exception;

public class OptimizationJobNotFoundException extends ApplicationException {

    public OptimizationJobNotFoundException(Long jobId) {
        super(ErrorCode.OPTIMIZATION_JOB_NOT_FOUND, "편성 작업을 찾을 수 없습니다. id=" + jobId);
    }
}
