package com.example.pl_timetable_project.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시간표에 담긴 강의(분반)의 한 요일-시간 슬롯을 나타내는 매핑 엔티티.
 * Course 도메인이 아직 없으므로 courseId 는 외부 참조용 식별자로만 보관하고,
 * 충돌 검사/학점 계산에 필요한 정보는 스냅샷 형태로 함께 저장한다.
 * 한 강의가 주 여러 요일에 걸쳐 있다면 같은 courseId 로 여러 행을 만든다.
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

    @Column(nullable = false)
    private Long courseId;

    @Column(nullable = false)
    private String courseName;

    private String professorName;

    @Column(nullable = false)
    private Integer credit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    public TimetableCourse(Long courseId, String courseName, String professorName, Integer credit,
                            DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.professorName = professorName;
        this.credit = credit;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    void assignTimetable(Timetable timetable) {
        this.timetable = timetable;
    }

    public boolean conflictsWith(TimetableCourse other) {
        if (this.dayOfWeek != other.dayOfWeek) {
            return false;
        }
        return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
    }
}
