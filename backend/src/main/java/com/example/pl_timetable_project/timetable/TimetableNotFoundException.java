package com.example.pl_timetable_project.timetable;

import com.example.pl_timetable_project.common.exception.BusinessException;

public class TimetableNotFoundException extends BusinessException {

    public TimetableNotFoundException(Long timetableId) {
        super(TimetableErrorCode.TIMETABLE_NOT_FOUND, "시간표를 찾을 수 없습니다. id=" + timetableId);
    }
}
