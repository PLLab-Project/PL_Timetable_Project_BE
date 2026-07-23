package com.example.pl_timetable_project.timetable.entity;

import com.example.pl_timetable_project.academic.section.AcademicSection;
import com.example.pl_timetable_project.academic.section.SectionReference;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시간표에 선택된 분반 한 건. 수업시간은 학사 원본을 읽은 시점의 스냅샷으로 보관한다.
 */
@Entity
@Table(name = "timetable_courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_id", nullable = false)
    private Timetable timetable;

    @Embedded
    private SectionReference section;

    @Column(name = "course_name", nullable = false)
    private String courseName;

    @Column(name = "professor_name")
    private String professorName;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal credits;

    @ElementCollection
    @CollectionTable(
            name = "timetable_course_meetings",
            joinColumns = @JoinColumn(name = "timetable_course_id"))
    @OrderColumn(name = "position")
    private List<TimetableMeeting> meetings = new ArrayList<>();

    public TimetableCourse(AcademicSection academicSection) {
        section = academicSection.reference();
        courseName = academicSection.courseName();
        professorName = academicSection.professorName();
        credits = academicSection.credits();
        meetings = new ArrayList<>(academicSection.meetings().stream()
                .map(meeting -> new TimetableMeeting(
                        meeting.dayOfWeek(), meeting.startTime(), meeting.endTime()))
                .toList());
    }

    void assignTimetable(Timetable timetable) {
        this.timetable = timetable;
    }

    public boolean conflictsWith(TimetableCourse other) {
        if (section.sameCourse(other.section)) {
            return true;
        }
        return meetings.stream().anyMatch(meeting ->
                other.meetings.stream().anyMatch(meeting::overlaps));
    }
}
