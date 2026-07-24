package com.example.pl_timetable_project.optimization;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class NoFeasibleTimetableException extends BusinessException {

    public NoFeasibleTimetableException(String message) {
        super(OptimizationErrorCode.NO_FEASIBLE_TIMETABLE, message);
    }
}
