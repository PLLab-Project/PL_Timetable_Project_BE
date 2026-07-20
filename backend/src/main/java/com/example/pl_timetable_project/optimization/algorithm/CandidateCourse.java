package com.example.pl_timetable_project.optimization.algorithm;

import java.util.List;

public record CandidateCourse(Long courseId, String courseName, String professorName, int credit,
                               List<CourseTimeSlot> timeSlots) {

    public boolean conflictsWith(CandidateCourse other) {
        for (CourseTimeSlot slot : timeSlots) {
            for (CourseTimeSlot otherSlot : other.timeSlots) {
                if (slot.overlaps(otherSlot)) {
                    return true;
                }
            }
        }
        return false;
    }
}
