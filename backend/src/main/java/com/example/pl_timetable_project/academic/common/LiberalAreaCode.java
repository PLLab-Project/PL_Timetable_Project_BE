package com.example.pl_timetable_project.academic.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * courses.category가 "교양선택(제N영역:이름)" 형태일 때 "제N영역:이름"만 뽑아낸다.
 * 전공("전공(학과명)")·교양필수·일반선택·교직 등 다른 형식은 매치되지 않아 null로
 * 남는다(방어적 처리). 실제 데이터 기준 graduation_liberal_area_requirements.area도
 * 같은 "제N영역:이름" 형식을 쓰므로, 자동편성(AcademicSectionQueryRepository)과
 * 졸업요건 추천(GraduationRecommendationService)이 이 파싱 규칙을 공유한다.
 *
 * <p>프론트 표시용 이름("과학과 기술")과 내부 코드("제3영역:과학과기술") 사이의
 * 변환도 함께 제공한다. 2025학번부터 적용되는 현재 교양선택 6개 영역만 고정
 * 매핑한다 — 2020~2024학번 교육과정은 영역 번호·이름이 달라(예: "과학과기술"이
 * 제3영역이 아니라 제4영역) 프론트가 애초에 그 이름들을 선택지로 제공하지 않는다.
 * (data/database/reference-data.sql.gz의 graduation_liberal_requirement_sets/
 * graduation_liberal_area_requirements 실데이터로 2026-08-11 확인.)
 */
public final class LiberalAreaCode {

    private static final Pattern CATEGORY_PATTERN =
            Pattern.compile("^교양선택\\((제\\d+영역:[^)]+)\\)$");

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^제\\d+영역:(.+)$");

    /** 표시용 이름(공백 제거 기준) → 내부 코드. 2025학번 이후 현재 교육과정 6개 영역. */
    private static final Map<String, String> LABEL_TO_CODE = new LinkedHashMap<>();

    static {
        LABEL_TO_CODE.put("인간과소통", "제1영역:인간과소통");
        LABEL_TO_CODE.put("사회와경제", "제2영역:사회와경제");
        LABEL_TO_CODE.put("과학과기술", "제3영역:과학과기술");
        LABEL_TO_CODE.put("예술과문화", "제4영역:예술과문화");
        LABEL_TO_CODE.put("융합과혁신", "제5영역:융합과혁신");
        LABEL_TO_CODE.put("디지털리터러시", "제6영역:AI·디지털리터러시");
    }

    private static final Map<String, String> CODE_TO_LABEL = new LinkedHashMap<>();

    static {
        LABEL_TO_CODE.forEach((label, code) -> CODE_TO_LABEL.put(code, label));
    }

    private LiberalAreaCode() {
    }

    public static String parse(String category) {
        if (category == null) {
            return null;
        }
        Matcher matcher = CATEGORY_PATTERN.matcher(category);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * 표시용 이름("과학과 기술")이나 이미 내부 코드("제3영역:과학과기술")로 들어온
     * 값을 내부 코드로 정규화한다. 공백만 다르거나 이미 코드 형식이면 그대로
     * 인정한다. 6개 영역 중 어디에도 해당하지 않으면(예: 다른 학번 교육과정의
     * 영역명, 혹은 애초에 교양 영역이 아닌 값) null을 반환한다 — 호출부가 원본
     * 값을 그대로 쓸지 판단해야 한다.
     */
    public static String fromDisplayLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.strip().replaceAll("\\s", "");
        if (normalized.isEmpty()) {
            return null;
        }
        if (CODE_TO_LABEL.containsKey(normalized)) {
            return normalized;
        }
        return LABEL_TO_CODE.get(normalized);
    }

    /**
     * 내부 코드를 표시용 이름으로 되돌린다. 현재 6개 영역 코드는 고정 매핑을
     * 쓰고, 그 외(예: 2020~2024학번 교육과정 코드)는 "제N영역:" 접두어만 벗겨
     * 최대한 읽을 수 있는 이름을 돌려준다. code가 null이면 null을 반환한다.
     */
    public static String toDisplayLabel(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.strip();
        String label = CODE_TO_LABEL.get(trimmed);
        if (label != null) {
            return label;
        }
        Matcher matcher = CODE_PATTERN.matcher(trimmed);
        return matcher.matches() ? matcher.group(1) : trimmed;
    }
}
