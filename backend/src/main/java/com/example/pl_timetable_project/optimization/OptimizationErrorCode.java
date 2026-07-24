package com.example.pl_timetable_project.optimization;

import com.example.pl_timetable_project.common.exception.ErrorCode;

/** 자동 편성 API에서 프론트에 전달하는 오류 코드입니다. */
public enum OptimizationErrorCode implements ErrorCode {

    INVALID_OPTIMIZATION_CONDITION(400, "INVALID_OPTIMIZATION_CONDITION", "자동 편성 조건이 올바르지 않습니다."),
    OPTIMIZATION_JOB_NOT_FOUND(404, "OPTIMIZATION_JOB_NOT_FOUND", "편성 작업을 찾을 수 없습니다."),
    REQUIRED_COURSE_CONFLICT(409, "REQUIRED_COURSE_CONFLICT", "필수 강의끼리 시간이 겹칩니다."),
    REQUIRED_COURSE_EXCLUDED_BY_CONDITION(409, "REQUIRED_COURSE_EXCLUDED_BY_CONDITION",
            "필수 강의 중 후보 목록에 없거나 조건(제외 요일/수업 가능 시간대)에 맞지 않는 강의가 있습니다."),
    OPTIMIZATION_ALREADY_FINISHED(409, "OPTIMIZATION_ALREADY_FINISHED", "이미 종료된 작업은 취소할 수 없습니다."),
    NO_FEASIBLE_TIMETABLE(422, "NO_FEASIBLE_TIMETABLE", "조건에 맞는 시간표를 찾을 수 없습니다."),
    OPTIMIZATION_TIMEOUT(504, "OPTIMIZATION_TIMEOUT", "시간표 편성 시간이 초과되었습니다."),
    OPTIMIZATION_FAILED(500, "OPTIMIZATION_FAILED", "시간표 편성 중 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;

    OptimizationErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
