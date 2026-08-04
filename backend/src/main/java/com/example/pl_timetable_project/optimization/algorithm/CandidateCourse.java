package com.example.pl_timetable_project.optimization.algorithm;

import com.example.pl_timetable_project.academic.section.SectionReference;
import java.util.List;

/**
 * restrictedAcademicUnitCodes: 이 강의가 후보로 포함되려면 사용자가 속해야 하는
 * 학과 코드 목록. 비어 있으면 학과 무관(교양 또는 미분류)이라 누구나 후보가 될 수 있다.
 *
 * <p>liberalAreaCode: courses.category가 "교양선택(제N영역:이름)"일 때만 채워지는
 * "제N영역:이름" 값. 그 외(전공·교양필수·일반선택 등)엔 null이다.</p>
 *
 * <p>prerequisiteCourseCodes: course_sequence_hints에 이 강의를 follow_up_course_code로
 * 등록해 둔 선수과목 코드 목록(수동 큐레이션). 비어 있으면 선수과목 힌트가 없다는 뜻이지,
 * 선수과목이 없다고 단정할 수 있는 정보는 아니다.</p>
 */
public record CandidateCourse(
        SectionReference section,
        String courseName,
        String professorName,
        int creditUnits,
        boolean required,
        List<CourseTimeSlot> timeSlots,
        List<String> restrictedAcademicUnitCodes,
        String liberalAreaCode,
        List<String> prerequisiteCourseCodes) {

    public CandidateCourse {
        timeSlots = List.copyOf(timeSlots);
        restrictedAcademicUnitCodes = List.copyOf(restrictedAcademicUnitCodes);
        prerequisiteCourseCodes = List.copyOf(prerequisiteCourseCodes);
    }

    public boolean conflictsWith(CandidateCourse other) {
        if (section.sameCourse(other.section)) {
            return true;
        }
        return timeSlots.stream().anyMatch(slot ->
                other.timeSlots.stream().anyMatch(slot::overlaps));
    }
}
