package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.academic.section.SectionReference;
import java.util.List;

public record CandidateCourse(
        SectionReference section,
        String courseName,
        String professorName,
        int creditUnits,
        boolean required,
        List<CourseTimeSlot> timeSlots) {

    public CandidateCourse {
        timeSlots = List.copyOf(timeSlots);
    }

    public boolean conflictsWith(CandidateCourse other) {
        if (section.sameCourse(other.section)) {
            return true;
        }
        return timeSlots.stream().anyMatch(slot ->
                other.timeSlots.stream().anyMatch(slot::overlaps));
    }
}
