package com.example.pl_timetable_project.academic.section;

import java.math.BigDecimal;
import java.util.List;

/**
 * restrictedAcademicUnitCodes: 이 분반이 수강 가능해야 하는 학과 코드 목록.
 * 비어 있으면 학과 무관(교양 또는 미분류)이라 누구나 후보로 포함될 수 있다는 뜻이다.
 */
public record AcademicSection(
        SectionReference reference,
        String courseName,
        String professorName,
        BigDecimal credits,
        List<AcademicMeeting> meetings,
        List<String> restrictedAcademicUnitCodes) {

    public AcademicSection {
        meetings = List.copyOf(meetings);
        restrictedAcademicUnitCodes = List.copyOf(restrictedAcademicUnitCodes);
    }
}
