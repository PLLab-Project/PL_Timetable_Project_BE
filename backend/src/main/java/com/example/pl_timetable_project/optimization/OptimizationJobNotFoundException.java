package com.example.pl_timetable_project.optimization;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class OptimizationJobNotFoundException extends BusinessException {

    public OptimizationJobNotFoundException(Long jobId) {
        super(OptimizationErrorCode.OPTIMIZATION_JOB_NOT_FOUND, "편성 작업을 찾을 수 없습니다. id=" + jobId);
    }
}
