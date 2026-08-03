package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.academic.section.SectionReference;
import java.util.List;

/**
 * restrictedAcademicUnitCodes: 이 강의가 후보로 포함되려면 사용자가 속해야 하는
 * 학과 코드 목록. 비어 있으면 학과 무관(교양 또는 미분류)이라 누구나 후보가 될 수 있다.
 */
public record CandidateCourse(
        SectionReference section,
        String courseName,
        String professorName,
        int creditUnits,
        boolean required,
        List<CourseTimeSlot> timeSlots,
        List<String> restrictedAcademicUnitCodes) {

    public CandidateCourse {
        timeSlots = List.copyOf(timeSlots);
        restrictedAcademicUnitCodes = List.copyOf(restrictedAcademicUnitCodes);
    }

    public boolean conflictsWith(CandidateCourse other) {
        if (section.sameCourse(other.section)) {
            return true;
        }
        return timeSlots.stream().anyMatch(slot ->
                other.timeSlots.stream().anyMatch(slot::overlaps));
    }
}
