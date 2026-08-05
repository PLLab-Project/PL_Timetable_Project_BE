package com.example.pl_timetable_project.optimization.algorithm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pl_timetable_project.academic.section.SectionReference;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * hardRestrictedAcademicUnitCode가 실린 후보를 CandidateCourseFilter가 실제로
 * 배제하는지 검증한다. 이 필드를 채우는 정규식/DB 매칭 자체는
 * AcademicSectionQueryRepositoryTest가 별도로 검증한다 — 여기서는 이미 확정된
 * 값을 필터가 올바르게 소비하는지만 본다.
 */
class CandidateCourseFilterTest {

    private final CandidateCourseFilter filter = new CandidateCourseFilter();

    @Test
    void excludesCourseHardRestrictedToAnotherAcademicUnit() {
        CandidateCourse restrictedToD2 = candidate("PHY100", "D2");
        OptimizationConstraints constraints = constraints(List.of("D1"));

        List<CandidateCourse> filtered = filter.filter(List.of(restrictedToD2), constraints);

        assertThat(filtered).isEmpty();
    }

    @Test
    void doesNotExcludeCourseRestrictedToTheUsersOwnAcademicUnit() {
        // (e) 본인 학과로 제한된 강의는 당연히 배제되지 않는다.
        CandidateCourse restrictedToD1 = candidate("CSE100", "D1");
        OptimizationConstraints constraints = constraints(List.of("D1"));

        List<CandidateCourse> filtered = filter.filter(List.of(restrictedToD1), constraints);

        assertThat(filtered).containsExactly(restrictedToD1);
    }

    @Test
    void doesNotExcludeCourseWithNoResolvedRestriction() {
        // hardRestrictedAcademicUnitCode가 null이면(패턴 불일치·예외 키워드 등)
        // 안전하게 배제하지 않는다.
        CandidateCourse unrestricted = candidate("GEN100", null);
        OptimizationConstraints constraints = constraints(List.of("D1"));

        List<CandidateCourse> filtered = filter.filter(List.of(unrestricted), constraints);

        assertThat(filtered).containsExactly(unrestricted);
    }

    @Test
    void doesNotExcludeCourseRestrictedToEitherOfTheUsersMultipleAcademicUnits() {
        // 복수전공생(주전공 D1, 복수전공 D2)에게는 두 학과 중 어느 쪽으로 제한된
        // 강의도 배제되지 않아야 한다.
        CandidateCourse restrictedToPrimary = candidate("CSE100", "D1");
        CandidateCourse restrictedToDoubleMajor = candidate("BUS100", "D2");
        OptimizationConstraints constraints = constraints(List.of("D1", "D2"));

        List<CandidateCourse> filtered = filter.filter(
                List.of(restrictedToPrimary, restrictedToDoubleMajor), constraints);

        assertThat(filtered).containsExactly(restrictedToPrimary, restrictedToDoubleMajor);
    }

    @Test
    void excludesCourseRestrictedToAcademicUnitOutsideAllOfTheUsersDeclaredPrograms() {
        // 복수전공생이라도 본인의 두 학과(D1, D2) 어디에도 속하지 않는 제3의 학과(D3)로
        // 진짜 제한된 강의는 그대로 배제된다.
        CandidateCourse restrictedToOtherUnit = candidate("PSY100", "D3");
        OptimizationConstraints constraints = constraints(List.of("D1", "D2"));

        List<CandidateCourse> filtered = filter.filter(
                List.of(restrictedToOtherUnit), constraints);

        assertThat(filtered).isEmpty();
    }

    @Test
    void doesNotExcludeWhenUserHasNoAcademicUnitCode() {
        // 사용자 학과 정보 자체가 없으면(프로필 미완성) 판단 근거가 없으므로
        // 안전하게 배제하지 않는다.
        CandidateCourse restrictedToD2 = candidate("PHY100", "D2");
        OptimizationConstraints constraints = constraints(List.of());

        List<CandidateCourse> filtered = filter.filter(List.of(restrictedToD2), constraints);

        assertThat(filtered).containsExactly(restrictedToD2);
    }

    private CandidateCourse candidate(String courseCode, String hardRestrictedAcademicUnitCode) {
        return new CandidateCourse(
                new SectionReference("2026-1", courseCode, "01"),
                "테스트과목",
                "담당교수",
                300,
                false,
                List.of(new CourseTimeSlot(
                        DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0))),
                List.of(),
                null,
                List.of(),
                hardRestrictedAcademicUnitCode);
    }

    private OptimizationConstraints constraints(List<String> userAcademicUnitCodes) {
        return new OptimizationConstraints(
                0,
                600,
                300,
                Set.of(),
                Set.of(),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                480,
                1_000,
                userAcademicUnitCodes);
    }
}
