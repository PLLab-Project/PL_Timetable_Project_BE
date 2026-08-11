package com.example.pl_timetable_project.academic.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LiberalAreaCodeTest {

    @Test
    void parsesAreaCodeFromCourseCategory() {
        assertThat(LiberalAreaCode.parse("교양선택(제3영역:과학과기술)"))
                .isEqualTo("제3영역:과학과기술");
        assertThat(LiberalAreaCode.parse("전공(컴퓨터공학과)")).isNull();
        assertThat(LiberalAreaCode.parse(null)).isNull();
    }

    @Test
    void convertsDisplayLabelWithSpacingToInternalCode() {
        assertThat(LiberalAreaCode.fromDisplayLabel("과학과 기술"))
                .isEqualTo("제3영역:과학과기술");
        assertThat(LiberalAreaCode.fromDisplayLabel("인간과 소통"))
                .isEqualTo("제1영역:인간과소통");
        assertThat(LiberalAreaCode.fromDisplayLabel("디지털리터러시"))
                .isEqualTo("제6영역:AI·디지털리터러시");
    }

    @Test
    void treatsAlreadyCanonicalCodeAsIdempotent() {
        assertThat(LiberalAreaCode.fromDisplayLabel("제3영역:과학과기술"))
                .isEqualTo("제3영역:과학과기술");
    }

    @Test
    void returnsNullForValuesThatAreNotOneOfTheSixCurrentAreas() {
        // "전공심화"처럼 교양 영역이 아닌 값, 그리고 2020~2024학번 교육과정의
        // 옛 영역명("역사와철학" 등 현재 6개 영역에 없는 이름)은 매핑하지 않는다 —
        // 호출부가 원본 값을 그대로 저장하도록 null로 신호를 보낸다.
        assertThat(LiberalAreaCode.fromDisplayLabel("전공심화")).isNull();
        assertThat(LiberalAreaCode.fromDisplayLabel("역사와철학")).isNull();
        assertThat(LiberalAreaCode.fromDisplayLabel(null)).isNull();
        assertThat(LiberalAreaCode.fromDisplayLabel("")).isNull();
        assertThat(LiberalAreaCode.fromDisplayLabel("   ")).isNull();
    }

    @Test
    void convertsInternalCodeBackToDisplayLabel() {
        assertThat(LiberalAreaCode.toDisplayLabel("제3영역:과학과기술"))
                .isEqualTo("과학과기술");
        assertThat(LiberalAreaCode.toDisplayLabel("제6영역:AI·디지털리터러시"))
                .isEqualTo("디지털리터러시");
    }

    @Test
    void fallsBackToStrippingThePrefixForUnknownCodes() {
        // 2020~2024학번 교육과정처럼 현재 6개 영역 밖의 코드는 고정 매핑에
        // 없으므로 "제N영역:" 접두어만 벗겨 최대한 읽을 수 있는 이름을 돌려준다.
        assertThat(LiberalAreaCode.toDisplayLabel("제2영역:역사와철학"))
                .isEqualTo("역사와철학");
    }

    @Test
    void returnsNullForNullCode() {
        assertThat(LiberalAreaCode.toDisplayLabel(null)).isNull();
    }
}
