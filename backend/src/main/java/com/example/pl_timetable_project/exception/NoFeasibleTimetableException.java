package com.example.pl_timetable_project.exception;

public class NoFeasibleTimetableException extends ApplicationException {

    public NoFeasibleTimetableException(String message) {
        super(ErrorCode.NO_FEASIBLE_TIMETABLE, message);
    }
}
