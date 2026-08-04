package com.example.pl_timetable_project.academic.section;

import java.math.BigDecimal;
import java.util.List;

/**
 * restrictedAcademicUnitCodes: 이 분반이 수강 가능해야 하는 학과 코드 목록.
 * 비어 있으면 학과 무관(교양 또는 미분류)이라 누구나 후보로 포함될 수 있다는 뜻이다.
 *
 * <p>liberalAreaCode: courses.category가 "교양선택(제N영역:이름)" 패턴일 때만
 * "제N영역:이름" 부분을 뽑아 담는다. 전공/교양필수/일반선택 등 패턴이 다르거나
 * 아예 없으면 null이다 — 이 필드는 교양 영역 판단 전용이지 과목 분류 전체를
 * 대표하지 않는다.</p>
 *
 * <p>hardRestrictedAcademicUnitCode: sections.notes가 "OO학과만신청가능"/
 * "OO학과만수강가능"으로 정확히 끝나고(뒤에 "/" 등 시간 개방 문구 없음), 유학생·
 * 복수전공·교직 같은 예외 키워드가 섞이지 않은 경우에만 채워지는, 그 학과의
 * 코드다. restrictedAcademicUnitCodes(소프트 가중치용)와 달리 이 필드는
 * "진짜 하드 배제" 판정에만 쓴다. null이면 안전하게 "제한 없음"으로 취급한다.</p>
 */
public record AcademicSection(
        SectionReference reference,
        String courseName,
        String professorName,
        BigDecimal credits,
        List<AcademicMeeting> meetings,
        List<String> restrictedAcademicUnitCodes,
        String liberalAreaCode,
        String hardRestrictedAcademicUnitCode) {

    public AcademicSection {
        meetings = List.copyOf(meetings);
        restrictedAcademicUnitCodes = List.copyOf(restrictedAcademicUnitCodes);
    }
}
