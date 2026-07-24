package com.example.pl_timetable_project.optimization;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class OptimizationAlreadyFinishedException extends BusinessException {

    public OptimizationAlreadyFinishedException(Long jobId) {
        super(OptimizationErrorCode.OPTIMIZATION_ALREADY_FINISHED, "이미 종료된 작업입니다. id=" + jobId);
    }
}
