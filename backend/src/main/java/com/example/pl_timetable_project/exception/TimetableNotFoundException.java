package com.example.pl_timetable_project.exception;

public class TimetableNotFoundException extends ApplicationException {

    public TimetableNotFoundException(Long timetableId) {
        super(ErrorCode.TIMETABLE_NOT_FOUND, "시간표를 찾을 수 없습니다. id=" + timetableId);
    }
}
