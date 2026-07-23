package com.example.pl_timetable_project.academic.section;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학기별 강의 분반의 정식 식별자.
 *
 * <p>학사 DB의 sections 기본키(semester_id, course_code, section_code)와 동일하다.</p>
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SectionReference implements Serializable {

    @Column(name = "semester_id", length = 20, nullable = false)
    private String semesterId;

    @Column(name = "course_code", length = 40, nullable = false)
    private String courseCode;

    @Column(name = "section_code", length = 20, nullable = false)
    private String sectionCode;

    public SectionReference(String semesterId, String courseCode, String sectionCode) {
        this.semesterId = requireText(semesterId, "semesterId");
        this.courseCode = requireText(courseCode, "courseCode");
        this.sectionCode = requireText(sectionCode, "sectionCode");
    }

    public boolean sameCourse(SectionReference other) {
        return semesterId.equals(other.semesterId) && courseCode.equals(other.courseCode);
    }

    public String displayKey() {
        return semesterId + ":" + courseCode + ":" + sectionCode;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
