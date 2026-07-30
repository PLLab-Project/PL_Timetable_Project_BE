package com.example.pl_timetable_project.common.openapi;

import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 프론트엔드가 생성 OpenAPI만 읽어도 호출 목적과 제약을 파악할 수 있도록
 * 컨트롤러별 설명, 파라미터 의미, 실제 요청 예제와 업무 오류를 한곳에서 관리합니다.
 */
final class OpenApiDocumentationCatalog {

    private static final Map<OperationKey, String> DESCRIPTIONS = descriptions();
    private static final Map<String, String> PARAMETER_DESCRIPTIONS =
            parameterDescriptions();
    private static final Map<OperationKey, Object> REQUEST_EXAMPLES =
            requestExamples();
    private static final Map<OperationKey, List<ErrorDocumentation>> BUSINESS_ERRORS =
            businessErrors();

    private OpenApiDocumentationCatalog() {
    }

    static List<Tag> tags() {
        return List.of(
                tag("인증", "학교 이메일 OTP·Google 로그인과 서버 세션 관리"),
                tag("사용자", "내 학생 프로필, 개인정보 동의와 회원 탈퇴"),
                tag("학과", "정규화된 대학·학과·전공 코드와 연도별 별칭 조회"),
                tag("학기", "학기 목록과 적재 데이터 버전 조회"),
                tag("강의", "학기별 강의 검색·필터·정렬과 분반 상세 조회"),
                tag("리뷰", "공개 리뷰 조회와 로그인 사용자의 리뷰 관리"),
                tag("이수과목", "내 이수·수강 중 과목과 학점 요약 관리"),
                tag("졸업요건", "입학연도·학과별 규칙 조회와 개인 이수 판정"),
                tag("시간표", "내 시간표와 검증된 학사 분반 구성 관리"),
                tag("자동편성", "OR-Tools 기반 비동기 시간표 자동 편성"),
                tag("시스템", "배포 버전과 서버 생존 상태 확인"));
    }

    static String description(String path, HttpMethod method, String summary) {
        return DESCRIPTIONS.getOrDefault(
                new OperationKey(method, path),
                summary + " 요청을 처리합니다. 요청·응답 필드와 보안 표시는 생성 스키마를 따릅니다.");
    }

    static String parameterDescription(String name) {
        return PARAMETER_DESCRIPTIONS.getOrDefault(
                name, "요청 대상을 식별하거나 조회 범위를 제한하는 값입니다.");
    }

    static Object requestExample(String path, HttpMethod method) {
        return REQUEST_EXAMPLES.get(new OperationKey(method, path));
    }

    static List<ErrorDocumentation> businessErrors(
            String path, HttpMethod method) {
        return BUSINESS_ERRORS.getOrDefault(
                new OperationKey(method, path), List.of());
    }

    private static Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }

    private static Map<OperationKey, String> descriptions() {
        Map<OperationKey, String> values = new LinkedHashMap<>();

        put(values, HttpMethod.POST, "/api/v1/auth/otp/request",
                "학번으로 학교 이메일 주소를 결정해 6자리 OTP를 전송합니다. "
                        + "재전송 대기시간 동안 같은 학번으로 다시 요청하면 429를 반환합니다.");
        put(values, HttpMethod.POST, "/api/v1/auth/otp/verify",
                "학번과 OTP를 검증하고 JSESSIONID 로그인 세션을 생성합니다. "
                        + "성공 응답의 Set-Cookie를 브라우저가 보관해야 이후 보호 API를 호출할 수 있습니다.");
        put(values, HttpMethod.GET, "/api/v1/auth/google",
                "Google OIDC Authorization Code 로그인을 시작합니다. "
                        + "브라우저는 Google 동의 화면으로 이동하고 성공하면 로컬 JSESSIONID 세션을 발급받습니다.");
        put(values, HttpMethod.GET, "/api/v1/auth/google/failure",
                "Google 인증 또는 로컬 계정 연결 실패를 GOOGLE_LOGIN_FAILED 표준 오류로 반환합니다.");
        put(values, HttpMethod.GET, "/api/v1/auth/csrf",
                "상태 변경 요청에 사용할 CSRF 토큰과 헤더 이름을 반환합니다. "
                        + "프론트와 API의 Origin이 달라도 쿠키를 직접 읽지 않고 응답의 data.token을 메모리에 보관해 사용할 수 있습니다.");
        put(values, HttpMethod.GET, "/api/v1/auth/session",
                "현재 JSESSIONID에 연결된 로그인 사용자와 세션 만료시각을 반환합니다. "
                        + "세션이 없거나 만료되면 AUTH_SESSION_EXPIRED를 반환합니다.");
        put(values, HttpMethod.POST, "/api/v1/auth/logout",
                "현재 로그인 세션을 서버에서 무효화합니다. "
                        + "상태 변경 요청이므로 /api/v1/auth/csrf 응답 토큰을 X-XSRF-TOKEN 헤더로 보내야 합니다.");

        put(values, HttpMethod.GET, "/api/v1/users/me",
                "로그인 사용자의 회원 정보와 학생 프로필을 조회합니다. "
                        + "departmentId는 academic_units의 정규 학과 코드입니다.");
        put(values, HttpMethod.PATCH, "/api/v1/users/me",
                "학번·이름·학년·학과·입학연도·학생구분·전공방식·튜토리얼 상태를 부분 수정합니다. "
                        + "null이거나 생략한 필드는 기존 값을 유지합니다.");
        put(values, HttpMethod.POST, "/api/v1/users/me/privacy-consents",
                "개인정보 처리방침 버전별 동의 여부를 저장합니다. 동일 버전의 기존 동의가 있으면 그 기록을 반환합니다.");
        put(values, HttpMethod.GET, "/api/v1/users/me/privacy-consents",
                "로그인 사용자의 개인정보 동의 이력을 최신순으로 반환합니다.");
        put(values, HttpMethod.DELETE, "/api/v1/users/me",
                "confirmed=true일 때 계정과 사용자 소유 리뷰·이수과목·시간표·자동편성 결과를 함께 삭제하고 세션을 종료합니다.");

        put(values, HttpMethod.GET, "/api/v1/departments",
                "현재 공식 학과·전공 코드를 페이지 단위로 조회합니다. "
                        + "과거 졸업요건 보존용 파생 코드는 기본 목록에서 제외됩니다.");
        put(values, HttpMethod.GET, "/api/v1/departments/colleges",
                "단과대 정규 코드와 이름, 현재 소속 학과 수를 반환합니다. "
                        + "선택한 코드는 /departments?collegeCode=...와 강의 필터에 사용합니다.");
        put(values, HttpMethod.GET, "/api/v1/departments/{code}",
                "정규 학과 코드의 단과대 정보와 연도별 명칭·별칭을 조회합니다.");
        put(values, HttpMethod.GET, "/api/v1/semesters",
                "적재된 학기 목록을 조회합니다. activeOnly=true가 기본값입니다.");
        put(values, HttpMethod.GET, "/api/v1/semesters/{semesterId}",
                "학기 ID로 준비일·데이터셋 버전·활성 여부를 조회합니다.");
        put(values, HttpMethod.GET, "/api/v1/semesters/{semesterId}/version",
                "프론트 캐시 무효화와 데이터 갱신 판단에 사용할 학기 데이터 버전과 체크섬을 반환합니다.");

        put(values, HttpMethod.GET, "/api/v1/courses",
                "학기 강의를 과목명·과목코드·교수·학과·단과대·영역·학점·요일로 검색합니다. "
                        + "category, academicUnitCode, collegeCode는 반복 또는 쉼표 구분 다중값을 허용하고, "
                        + "디지털리터러시 별칭은 공식 제6영역 분류로 변환합니다.");
        put(values, HttpMethod.GET, "/api/v1/sections",
                "메인 시간표 화면의 강의 카드용 분반 목록입니다. 과목·교수 검색, 학과·이수구분·"
                        + "대상 학년·학점·요일 필터를 다중값으로 처리합니다. 교필 등 이수구분 조회 시 "
                        + "로그인 사용자의 학과 분류를 우선 정렬하고, "
                        + "각 항목에 비고와 전체 수업시간·강의실·분류 문맥을 반환합니다.");
        put(values, HttpMethod.GET, "/api/v1/courses/{semesterId}/{courseCode}",
                "학기와 과목코드로 강의 기본정보, 연결 학과, 분반 수와 리뷰 집계를 조회합니다.");
        put(values, HttpMethod.GET, "/api/v1/courses/{semesterId}/{courseCode}/sections",
                "같은 과목의 교수·분반·수업시간·강의실을 비교할 수 있도록 전체 분반을 반환합니다.");
        put(values, HttpMethod.GET,
                "/api/v1/courses/{semesterId}/{courseCode}/sections/{sectionCode}",
                "정확한 학기·과목·분반 키로 분반 상세와 모든 수업시간을 조회합니다.");

        put(values, HttpMethod.GET, "/api/v1/courses/reviews",
                "작성자 개인정보를 제외한 공개 리뷰를 최신순으로 페이지 조회합니다.");
        put(values, HttpMethod.GET, "/api/v1/courses/reviews/{courseCode}",
                "과목코드에 해당하는 공개 리뷰를 학기 선택 조건과 함께 조회합니다.");
        put(values, HttpMethod.GET,
                "/api/v1/courses/reviews/{courseCode}/professors/{professor}",
                "특정 과목과 교수 조합의 공개 리뷰를 조회합니다.");
        put(values, HttpMethod.POST, "/api/v1/reviews",
                "로그인 사용자가 실제 학기 강의와 교수에 대한 별점·리뷰를 작성합니다. "
                        + "동일 학기·과목·교수 조합에는 한 번만 작성할 수 있습니다.");
        put(values, HttpMethod.GET, "/api/v1/reviews/me",
                "로그인 사용자가 작성한 리뷰를 학기 선택 조건과 함께 최신순으로 조회합니다.");
        put(values, HttpMethod.PATCH, "/api/v1/reviews/{reviewId}",
                "로그인 사용자가 소유한 리뷰의 별점과 내용을 수정합니다.");
        put(values, HttpMethod.DELETE, "/api/v1/reviews/{reviewId}",
                "로그인 사용자가 소유한 리뷰를 삭제합니다. 성공 시 data는 null입니다.");

        put(values, HttpMethod.POST, "/api/v1/completed-courses",
                "이수·수강 중 과목을 직접 등록합니다. P/N 과목도 credits에는 실제 인정 학점을 넣고 "
                        + "gradingBasis=PASS_FAIL, gradeValue=P 또는 N으로 분리합니다.");
        put(values, HttpMethod.POST, "/api/v1/completed-courses/ocr",
                "성적표 이미지(JPEG·PNG·WebP·HEIC·HEIF)를 Gemini 3.5 Flash-Lite 비전으로 인식합니다. "
                        + "7MB 이하 원본은 저장하지 않으며, 반환 텍스트를 확인·보정한 뒤 직접 등록 API로 저장합니다.");
        put(values, HttpMethod.GET, "/api/v1/completed-courses",
                "로그인 사용자의 이수과목을 상태와 학기 조건으로 조회합니다.");
        put(values, HttpMethod.GET, "/api/v1/completed-courses/summary",
                "전체·이수 완료·수강 중 학점과 이수구분·영역·상태별 학점 합계를 반환합니다.");
        put(values, HttpMethod.GET, "/api/v1/completed-courses/{completedCourseId}",
                "로그인 사용자가 소유한 이수과목 한 건을 조회합니다.");
        put(values, HttpMethod.PATCH, "/api/v1/completed-courses/{completedCourseId}",
                "로그인 사용자가 소유한 이수과목의 전달된 필드만 수정합니다.");
        put(values, HttpMethod.DELETE, "/api/v1/completed-courses/{completedCourseId}",
                "로그인 사용자가 소유한 이수과목을 삭제합니다. 성공 시 data는 null입니다.");
        put(values, HttpMethod.POST,
                "/api/v1/completed-courses/imports/timetables/{timetableId}",
                "내 시간표의 검증된 분반을 IN_PROGRESS 이수과목으로 가져옵니다. "
                        + "이미 가져온 동일 분반은 중복 생성하지 않고 skippedCount에 포함합니다.");
        put(values, HttpMethod.POST,
                "/api/v1/completed-courses/{completedCourseId}/complete",
                "IN_PROGRESS 상태의 이수과목을 COMPLETED로 전환합니다. 다른 상태에서는 409를 반환합니다.");

        put(values, HttpMethod.GET, "/api/v1/graduation/rules",
                "입학연도·학과·학생구분·전공방식에 맞는 졸업학점·교양영역·필수과목과 공식 근거를 조회합니다.");
        put(values, HttpMethod.GET, "/api/v1/graduation/evaluation",
                "내 학생 프로필과 COMPLETED 이수과목으로 졸업요건 충족 여부, 부족 학점·영역·과목과 추천을 계산합니다.");
        put(values, HttpMethod.GET, "/api/v1/graduation/me/evaluation",
                "내 졸업요건 판정의 호환 별칭 경로입니다. /api/v1/graduation/evaluation과 같은 결과를 반환합니다.");

        put(values, HttpMethod.POST, "/api/v1/timetables",
                "학기와 정식 분반 키로 내 시간표를 생성합니다. "
                        + "과목명·교수·학점·수업시간은 요청값을 받지 않고 학사 DB에서 검증합니다.");
        put(values, HttpMethod.GET, "/api/v1/timetables",
                "로그인 사용자의 시간표 목록과 각 시간표의 학점·분반 수 요약을 반환합니다.");
        put(values, HttpMethod.GET, "/api/v1/timetables/{timetableId}",
                "내 시간표의 검증된 분반, 수업시간, 총학점과 수업 사이 공강을 조회합니다.");
        put(values, HttpMethod.PATCH, "/api/v1/timetables/{timetableId}",
                "내 시간표 이름 또는 즐겨찾기 상태를 부분 변경합니다.");
        put(values, HttpMethod.PATCH, "/api/v1/timetables/{timetableId}/favorite",
                "시간표 한 개의 즐겨찾기 상태를 변경합니다. 여러 시간표를 동시에 즐겨찾기할 수 있습니다.");
        put(values, HttpMethod.PATCH, "/api/v1/timetables/{timetableId}/sections",
                "내 시간표의 분반 구성을 요청 목록으로 전체 교체합니다. 중복 과목이나 시간 충돌은 409를 반환합니다.");
        put(values, HttpMethod.POST, "/api/v1/timetables/{timetableId}/sections",
                "내 시간표에 학사 DB의 분반 하나를 추가합니다. 중복 과목이나 시간 충돌은 409를 반환합니다.");
        put(values, HttpMethod.DELETE,
                "/api/v1/timetables/{timetableId}/sections/{timetableCourseId}",
                "시간표 항목 ID로 내 시간표에서 분반 하나를 제거합니다.");
        put(values, HttpMethod.DELETE, "/api/v1/timetables/{timetableId}",
                "내 시간표와 포함된 분반 스냅샷을 삭제합니다. 성공 시 data는 null입니다.");

        put(values, HttpMethod.POST, "/api/v1/optimizations",
                "내 시간표 학기의 후보 분반과 학점·요일·시간 제약으로 비동기 자동편성 작업을 생성합니다. "
                        + "candidateCourses를 비우면 서버가 전체 분반을 직접 조회하므로 100개 제한이 없고, "
                        + "availableTimes 다중 선호 시간과 blockedTimes 공강 고정을 지원합니다.");
        put(values, HttpMethod.GET, "/api/v1/optimizations/{jobId}",
                "내 자동편성 작업의 진행 상태, 실패 이유와 점수순 최대 3개 결과를 조회합니다.");
        put(values, HttpMethod.DELETE, "/api/v1/optimizations/{jobId}",
                "완료 전인 내 자동편성 작업을 취소합니다. 이미 종료된 작업은 409를 반환합니다.");
        put(values, HttpMethod.POST, "/api/v1/optimizations/{jobId}/results/{rank}/apply",
                "성공한 자동편성 결과의 분반을 작업 생성 시 지정한 내 시간표에 전체 저장합니다. "
                        + "학사 DB 분반을 다시 검증하고 중복·충돌이 있으면 저장하지 않습니다.");

        put(values, HttpMethod.GET, "/api/v1/health/live",
                "서버 프로세스 생존 여부와 현재 배포된 애플리케이션 버전·Git 커밋을 반환합니다. 인증이 필요하지 않습니다.");
        return Map.copyOf(values);
    }

    private static Map<String, String> parameterDescriptions() {
        return Map.ofEntries(
                Map.entry("semesterId", "학기 식별자입니다. 예: 2026-1"),
                Map.entry("timetableId", "로그인 사용자가 소유한 시간표 ID입니다."),
                Map.entry("page", "0부터 시작하는 페이지 번호입니다."),
                Map.entry("size", "페이지 크기입니다. 기본 20, 최대 100입니다."),
                Map.entry("courseCode", "학교 학사 데이터의 과목코드입니다."),
                Map.entry("completedCourseId", "로그인 사용자가 소유한 이수과목 ID입니다."),
                Map.entry("reviewId", "수정·삭제할 리뷰 UUID입니다."),
                Map.entry("jobId", "로그인 사용자가 생성한 자동편성 작업 ID입니다."),
                Map.entry("query", "이름 또는 코드에 적용할 부분 검색어입니다."),
                Map.entry("professor", "교수명 부분 검색 또는 정확한 교수명 조건입니다."),
                Map.entry("status", "이수과목 상태입니다: COMPLETED, IN_PROGRESS, PLANNED, FAILED, WITHDRAWN"),
                Map.entry("semester", "이수과목에 기록된 학기 문자열입니다. 예: 2026-1"),
                Map.entry("activeOnly", "true이면 활성 학기만 반환합니다. 기본값은 true입니다."),
                Map.entry("admissionYear", "입학연도입니다. 1900~2100 범위입니다."),
                Map.entry("academicUnit", "정규 학과 코드, 학과명 또는 등록된 연도별 별칭입니다."),
                Map.entry("studentType", "졸업요건 원천 데이터의 학생 구분입니다."),
                Map.entry("programPath", "전공 방식입니다: ADVANCED_MAJOR, DOUBLE_MAJOR, MINOR, MICRO_MAJOR"),
                Map.entry("collegeCode", "단과대 코드 다중 선택 조건입니다. 반복 파라미터 또는 쉼표 구분값을 사용합니다."),
                Map.entry("currentOnly", "true이면 현재 교육과정에 존재하는 공식 학과만 반환합니다."),
                Map.entry("code", "academic_units의 정규 학과·전공 코드입니다."),
                Map.entry("category", "강의 편성 분류 다중 선택 조건입니다. 디지털리터러시 별칭도 사용할 수 있습니다."),
                Map.entry("academicUnitCode", "분반과 연결된 정규 학과·전공 코드 다중 선택 조건입니다."),
                Map.entry("preferredAcademicUnitCode", "이수구분 필터 결과에서 우선 정렬할 학과 코드입니다. 로그인 프로필이 있으면 생략할 수 있습니다."),
                Map.entry("completionCategory", "분반의 학과별 이수구분 다중 선택 조건입니다. 전공필수/전필 등 긴 이름과 축약형을 모두 허용합니다."),
                Map.entry("targetGrade", "분반 수강 대상 학년 다중 선택 조건입니다. 1~4 또는 1학년~4학년 형식입니다."),
                Map.entry("credits", "강의 학점 정확히 일치 조건입니다."),
                Map.entry("day", "수업 요일입니다. 월~일 또는 MONDAY~SUNDAY를 사용할 수 있습니다."),
                Map.entry("sort", "DEFAULT(원본 카탈로그 순), NAME_ASC, NAME_DESC, REVIEW_COUNT_DESC, RATING_DESC, POPULARITY_DESC 중 하나입니다."),
                Map.entry("sectionCode", "학기·과목 안에서 분반을 식별하는 코드입니다."),
                Map.entry("timetableCourseId", "시간표 상세 sections[].id로 반환되는 시간표 항목 ID입니다."),
                Map.entry("rank", "자동편성 결과의 1부터 시작하는 순위입니다."),
                Map.entry("file", "7MB 이하 성적표 이미지 파일입니다. JPEG, PNG, WebP, HEIC, HEIF를 지원합니다."));
    }

    private static Map<OperationKey, Object> requestExamples() {
        Map<OperationKey, Object> values = new LinkedHashMap<>();
        example(values, HttpMethod.POST, "/api/v1/auth/otp/request",
                Map.of("studentNumber", "20201234"));
        example(values, HttpMethod.POST, "/api/v1/auth/otp/verify",
                Map.of("studentNumber", "20201234", "code", "123456"));
        example(values, HttpMethod.PATCH, "/api/v1/users/me",
                ordered(
                        "studentNumber", "20261234",
                        "name", "홍길동",
                        "grade", 3,
                        "departmentId", "AA0846",
                        "admissionYear", 2022,
                        "studentType", "DOMESTIC",
                        "programPath", "ADVANCED_MAJOR",
                        "tutorialCompleted", true));
        example(values, HttpMethod.POST, "/api/v1/users/me/privacy-consents",
                Map.of("consentVersion", "privacy-v1", "agreed", true));
        example(values, HttpMethod.DELETE, "/api/v1/users/me",
                Map.of("confirmed", true));
        example(values, HttpMethod.POST, "/api/v1/reviews",
                ordered(
                        "semesterId", "2026-1",
                        "courseCode", "855121",
                        "professor", "홍교수",
                        "rating", 5,
                        "content", "설명이 명확하고 실습이 유익했습니다."));
        example(values, HttpMethod.PATCH, "/api/v1/reviews/{reviewId}",
                Map.of("rating", 4, "content", "수정한 리뷰 내용입니다."));
        example(values, HttpMethod.POST, "/api/v1/completed-courses",
                ordered(
                        "courseCode", "855121",
                        "courseName", "1인미디어제작실습",
                        "credits", 2.0,
                        "gradingBasis", "PASS_FAIL",
                        "gradeValue", "P",
                        "category", "전공선택",
                        "area", "전공심화",
                        "semester", "2026-1",
                        "status", "IN_PROGRESS"));
        example(values, HttpMethod.PATCH,
                "/api/v1/completed-courses/{completedCourseId}",
                Map.of("area", "전공심화", "status", "COMPLETED"));
        example(values, HttpMethod.POST, "/api/v1/timetables",
                ordered(
                        "name", "2026-1 전공 시간표",
                        "semesterId", "2026-1",
                        "sections", List.of(
                                Map.of("courseCode", "855121", "sectionCode", "01"))));
        example(values, HttpMethod.PATCH, "/api/v1/timetables/{timetableId}",
                Map.of("name", "공강 우선 시간표", "favorite", true));
        example(values, HttpMethod.PATCH, "/api/v1/timetables/{timetableId}/favorite",
                Map.of("favorite", true));
        example(values, HttpMethod.PATCH,
                "/api/v1/timetables/{timetableId}/sections",
                Map.of("sections", List.of(
                        Map.of("courseCode", "855121", "sectionCode", "01"),
                        Map.of("courseCode", "000111", "sectionCode", "02"))));
        example(values, HttpMethod.POST,
                "/api/v1/timetables/{timetableId}/sections",
                Map.of("courseCode", "855121", "sectionCode", "01"));
        example(values, HttpMethod.POST, "/api/v1/optimizations",
                ordered(
                        "timetableId", 12,
                        "minCredits", 12.0,
                        "maxCredits", 18.0,
                        "targetCredits", 15.0,
                        "excludedDays", List.of("FRIDAY"),
                        "availableTimes", List.of(
                                Map.of("startTime", "09:00:00", "endTime", "12:00:00"),
                                Map.of("startTime", "13:00:00", "endTime", "18:00:00")),
                        "blockedTimes", List.of(Map.of(
                                "dayOfWeek", "WEDNESDAY",
                                "startTime", "15:00:00",
                                "endTime", "17:00:00")),
                        "lunchTime", Map.of(
                                "startTime", "12:00:00", "endTime", "13:00:00"),
                        "maxDailyClassMinutes", 360,
                        "candidateCourses", List.of(),
                        "requiredCourses", List.of(
                                Map.of(
                                        "courseCode", "855121",
                                        "sectionCode", "01",
                                        "required", true))));
        return Map.copyOf(values);
    }

    private static Map<OperationKey, List<ErrorDocumentation>> businessErrors() {
        Map<OperationKey, List<ErrorDocumentation>> values = new LinkedHashMap<>();
        errors(values, HttpMethod.POST, "/api/v1/auth/otp/request",
                error(429, "TOO_MANY_REQUESTS", "재전송 대기시간이 지나지 않았습니다."),
                error(503, "EMAIL_SEND_FAILED", "인증 이메일을 전송하지 못했습니다."));
        errors(values, HttpMethod.POST, "/api/v1/auth/otp/verify",
                error(429, "TOO_MANY_ATTEMPTS", "인증번호 확인 횟수를 초과했습니다."));
        errors(values, HttpMethod.GET, "/api/v1/auth/google",
                error(503, "GOOGLE_LOGIN_NOT_CONFIGURED", "배포 환경에 Google OAuth 클라이언트가 설정되지 않았습니다."));
        errors(values, HttpMethod.GET, "/api/v1/auth/google/failure",
                error(401, "GOOGLE_LOGIN_FAILED", "Google 인증 또는 계정 연결에 실패했습니다."));

        notFound(values, HttpMethod.GET, "/api/v1/users/me", "USER_NOT_FOUND", "사용자 정보를 찾을 수 없습니다.");
        notFound(values, HttpMethod.PATCH, "/api/v1/users/me", "DEPARTMENT_NOT_FOUND", "학과 정보를 찾을 수 없습니다.");
        notFound(values, HttpMethod.GET, "/api/v1/departments/{code}", "ACADEMIC_RESOURCE_NOT_FOUND", "학과 정보를 찾을 수 없습니다.");
        notFound(values, HttpMethod.GET, "/api/v1/semesters/{semesterId}", "ACADEMIC_RESOURCE_NOT_FOUND", "학기를 찾을 수 없습니다.");
        notFound(values, HttpMethod.GET, "/api/v1/semesters/{semesterId}/version", "ACADEMIC_RESOURCE_NOT_FOUND", "학기 버전을 찾을 수 없습니다.");
        notFound(values, HttpMethod.GET, "/api/v1/courses/{semesterId}/{courseCode}", "ACADEMIC_RESOURCE_NOT_FOUND", "강의를 찾을 수 없습니다.");
        notFound(values, HttpMethod.GET, "/api/v1/sections", "ACADEMIC_RESOURCE_NOT_FOUND", "학기를 찾을 수 없습니다.");
        notFound(values, HttpMethod.GET, "/api/v1/courses/{semesterId}/{courseCode}/sections", "ACADEMIC_RESOURCE_NOT_FOUND", "강의를 찾을 수 없습니다.");
        notFound(values, HttpMethod.GET, "/api/v1/courses/{semesterId}/{courseCode}/sections/{sectionCode}", "ACADEMIC_RESOURCE_NOT_FOUND", "분반을 찾을 수 없습니다.");
        notFound(values, HttpMethod.PATCH, "/api/v1/reviews/{reviewId}", "ACADEMIC_RESOURCE_NOT_FOUND", "리뷰를 찾을 수 없습니다.");
        notFound(values, HttpMethod.DELETE, "/api/v1/reviews/{reviewId}", "ACADEMIC_RESOURCE_NOT_FOUND", "리뷰를 찾을 수 없습니다.");
        notFoundForCompletedCourse(values, HttpMethod.GET, "/api/v1/completed-courses/{completedCourseId}");
        notFoundForCompletedCourse(values, HttpMethod.PATCH, "/api/v1/completed-courses/{completedCourseId}");
        notFoundForCompletedCourse(values, HttpMethod.DELETE, "/api/v1/completed-courses/{completedCourseId}");
        notFoundForCompletedCourse(values, HttpMethod.POST, "/api/v1/completed-courses/{completedCourseId}/complete");
        notFound(values, HttpMethod.POST, "/api/v1/completed-courses/imports/timetables/{timetableId}",
                "COMPLETED_COURSE_TIMETABLE_NOT_FOUND", "가져올 시간표를 찾을 수 없습니다.");
        addError(values, HttpMethod.POST, "/api/v1/completed-courses/{completedCourseId}/complete",
                error(409, "COMPLETED_COURSE_INVALID_STATUS_TRANSITION", "수강 중인 과목만 이수 완료로 전환할 수 있습니다."));
        errors(values, HttpMethod.POST, "/api/v1/completed-courses/ocr",
                error(413, "COMPLETED_COURSE_OCR_FILE_TOO_LARGE", "성적표 이미지 크기가 허용 범위를 초과했습니다."),
                error(415, "COMPLETED_COURSE_OCR_UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 성적표 이미지 형식입니다."),
                error(502, "COMPLETED_COURSE_OCR_RECOGNITION_FAILED", "성적표 이미지를 인식하지 못했습니다."),
                error(503, "COMPLETED_COURSE_OCR_NOT_CONFIGURED", "OCR 서비스가 설정되지 않았습니다."));

        for (HttpMethod method : List.of(
                HttpMethod.GET, HttpMethod.PATCH, HttpMethod.DELETE)) {
            notFound(values, method, "/api/v1/timetables/{timetableId}",
                    "TIMETABLE_NOT_FOUND", "시간표를 찾을 수 없습니다.");
        }
        for (HttpMethod method : List.of(HttpMethod.PATCH, HttpMethod.POST)) {
            notFound(values, method, "/api/v1/timetables/{timetableId}/sections",
                    "TIMETABLE_NOT_FOUND", "시간표를 찾을 수 없습니다.");
            addError(values, method, "/api/v1/timetables/{timetableId}/sections",
                    error(409, "SECTION_CONFLICT", "같은 과목이 중복되거나 강의 시간이 겹칩니다."));
        }
        notFound(values, HttpMethod.DELETE,
                "/api/v1/timetables/{timetableId}/sections/{timetableCourseId}",
                "TIMETABLE_NOT_FOUND", "시간표 또는 분반 항목을 찾을 수 없습니다.");
        notFound(values, HttpMethod.PATCH,
                "/api/v1/timetables/{timetableId}/favorite",
                "TIMETABLE_NOT_FOUND", "시간표를 찾을 수 없습니다.");
        addError(values, HttpMethod.POST, "/api/v1/timetables",
                error(409, "SECTION_CONFLICT", "같은 과목이 중복되거나 강의 시간이 겹칩니다."));

        notFound(values, HttpMethod.GET, "/api/v1/optimizations/{jobId}",
                "OPTIMIZATION_JOB_NOT_FOUND", "편성 작업을 찾을 수 없습니다.");
        notFound(values, HttpMethod.DELETE, "/api/v1/optimizations/{jobId}",
                "OPTIMIZATION_JOB_NOT_FOUND", "편성 작업을 찾을 수 없습니다.");
        addError(values, HttpMethod.DELETE, "/api/v1/optimizations/{jobId}",
                error(409, "OPTIMIZATION_ALREADY_FINISHED", "이미 종료된 작업은 취소할 수 없습니다."));
        addError(values, HttpMethod.POST, "/api/v1/optimizations",
                error(409, "REQUIRED_COURSE_CONFLICT", "필수 강의끼리 시간이 겹칩니다."),
                error(422, "NO_FEASIBLE_TIMETABLE", "조건에 맞는 시간표를 찾을 수 없습니다."));
        notFound(values, HttpMethod.POST,
                "/api/v1/optimizations/{jobId}/results/{rank}/apply",
                "OPTIMIZATION_JOB_NOT_FOUND", "편성 작업을 찾을 수 없습니다.");
        return Map.copyOf(values);
    }

    private static void notFoundForCompletedCourse(
            Map<OperationKey, List<ErrorDocumentation>> values,
            HttpMethod method,
            String path) {
        notFound(values, method, path,
                "COMPLETED_COURSE_NOT_FOUND", "이수과목을 찾을 수 없습니다.");
    }

    private static void notFound(
            Map<OperationKey, List<ErrorDocumentation>> values,
            HttpMethod method,
            String path,
            String code,
            String message) {
        addError(values, method, path, error(404, code, message));
    }

    private static void addError(
            Map<OperationKey, List<ErrorDocumentation>> values,
            HttpMethod method,
            String path,
            ErrorDocumentation... errors) {
        OperationKey key = new OperationKey(method, path);
        List<ErrorDocumentation> merged = new java.util.ArrayList<>(
                values.getOrDefault(key, List.of()));
        merged.addAll(List.of(errors));
        values.put(key, List.copyOf(merged));
    }

    private static void errors(
            Map<OperationKey, List<ErrorDocumentation>> values,
            HttpMethod method,
            String path,
            ErrorDocumentation... errors) {
        values.put(new OperationKey(method, path), List.of(errors));
    }

    private static ErrorDocumentation error(
            int status, String code, String message) {
        return new ErrorDocumentation(status, code, message);
    }

    private static void put(
            Map<OperationKey, String> values,
            HttpMethod method,
            String path,
            String description) {
        values.put(new OperationKey(method, path), description);
    }

    private static void example(
            Map<OperationKey, Object> values,
            HttpMethod method,
            String path,
            Object example) {
        values.put(new OperationKey(method, path), example);
    }

    private static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            values.put((String) keyValues[index], keyValues[index + 1]);
        }
        return values;
    }

    record ErrorDocumentation(int status, String code, String message) {
    }

    private record OperationKey(HttpMethod method, String path) {
    }
}
