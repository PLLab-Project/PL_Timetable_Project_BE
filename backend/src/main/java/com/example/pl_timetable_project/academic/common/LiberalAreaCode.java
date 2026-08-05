package com.example.pl_timetable_project.academic.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * courses.category가 "교양선택(제N영역:이름)" 형태일 때 "제N영역:이름"만 뽑아낸다.
 * 전공("전공(학과명)")·교양필수·일반선택·교직 등 다른 형식은 매치되지 않아 null로
 * 남는다(방어적 처리). 실제 데이터 기준 graduation_liberal_area_requirements.area도
 * 같은 "제N영역:이름" 형식을 쓰므로, 자동편성(AcademicSectionQueryRepository)과
 * 졸업요건 추천(GraduationRecommendationService)이 이 파싱 규칙을 공유한다.
 */
public final class LiberalAreaCode {

    private static final Pattern PATTERN =
            Pattern.compile("^교양선택\\((제\\d+영역:[^)]+)\\)$");

    private LiberalAreaCode() {
    }

    public static String parse(String category) {
        if (category == null) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(category);
        return matcher.matches() ? matcher.group(1) : null;
    }
}
