package com.example.pl_timetable_project.academic.section;

import java.math.BigDecimal;
import java.util.List;

public record AcademicSection(
        SectionReference reference,
        String courseName,
        String professorName,
        BigDecimal credits,
        List<AcademicMeeting> meetings) {

    public AcademicSection {
        meetings = List.copyOf(meetings);
    }
}
