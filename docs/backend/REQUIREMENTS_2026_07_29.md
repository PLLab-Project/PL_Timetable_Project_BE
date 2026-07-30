# 2026-07-29 프론트 연동 요구사항 반영 결과

아래 내용은 2026-07-29 전달된 19개 요구사항을 번호 그대로 추적한 백엔드 변경
결과다. 실행 중인 정확한 요청·응답 스키마는 `/v3/api-docs`를 기준으로 한다.

1. **학년, 전공/영역 다중 선택**
   - `/api/v1/courses`, `/api/v1/sections`의 `category`,
     `academicUnitCode`, `collegeCode`를 다중값으로 변경했다.
   - `/api/v1/sections`의 `completionCategory`, `targetGrade`도 다중값이다.
   - 반복 파라미터와 쉼표 구분값을 모두 허용한다.
2. **단과대 필터**
   - `GET /api/v1/departments/colleges`로 단과대 코드·이름·학과 수를 조회한다.
   - `GET /api/v1/departments?collegeCode=...`로 소속 학과를 조회하고,
     강의·분반 검색에도 같은 `collegeCode`를 직접 전달할 수 있다.
3. **디지털리터러시**
   - 과목명에 정확히 “디지털리터러시”가 들어간 강의는 운영 DB 기준 0건이다.
   - 실제 데이터는 `교양선택(제6영역:AI·디지털리터러시)` 영역으로 존재하며
     2026-1 23과목, 2026-2 21과목이다.
   - `category=디지털리터러시`를 공식 영역명으로 변환하도록 보완했다.
4. **졸업요건 입력값**
   - `PATCH /api/v1/users/me`에 `admissionYear`, `studentType`,
     `programPath`를 추가했다.
   - 세 필드는 입학연도·학생구분·전공방식 조합으로 졸업요건 원본 프로필을
     고르는 키이므로 졸업판정 전에 필요하다.
   - 응답에 `graduationProfileCompleted`를 추가했다.
5. **자동 편성 흐름**
   - `candidateCourses`를 비우면 서버가 시간표 학기의 전체 분반을 직접 조회한다.
   - 성공 결과는
     `POST /api/v1/optimizations/{jobId}/results/{rank}/apply`로 대상 시간표에 저장한다.
6. **자동 편성 공간 시간 지정**
   - `blockedTimes: [{dayOfWeek,startTime,endTime}]`를 추가했다.
   - 해당 범위와 겹치는 모든 분반을 후보에서 제외한다.
7. **시간표 저장**
   - `/api/v1/timetables` CRUD와 분반 추가·전체 교체는 기존대로 서버 DB에 저장한다.
   - 자동편성 결과도 5번의 `apply` API로 실제 시간표에 저장한다.
8. **정렬 조건**
   - `DEFAULT`를 별도 값으로 추가했다.
   - 분반은 원본 카탈로그 `sourcePage/sourceRow` 순, 과목 집계는 과목코드 순이다.
   - `NAME_ASC`는 이름순으로 유지한다.
9. **전공필수/전공선택**
   - 2026-2 공식 원본의 학과별 분류 문맥 1,568건을 사용해 `전필`, `전선`,
     `전기`, `교필`, `교선` 등을 구분한다.
   - 2026-1 원본에는 이 분류 문맥이 없으므로 값을 임의 생성하지 않는다.
10. **필터링 시 정렬 순서**
    - 이수구분 필터가 있을 때 로그인 사용자의 `academicUnitCode`에 맞는 분반을
      우선 정렬한다.
    - 공개 호출은 `preferredAcademicUnitCode`를 명시할 수 있다.
11. **즐겨찾기**
    - 시간표 응답에 `favorite`를 추가했다.
    - `PATCH /api/v1/timetables/{id}/favorite`로 변경하며 여러 시간표를 동시에
      즐겨찾기할 수 있다.
12. **선호 시간대 중복 선택**
    - `availableTimes` 배열을 추가했다. 각 강의 시간은 배열 중 하나에 완전히
      포함되어야 한다.
    - 기존 단일 `availableTime`도 호환용으로 유지한다.
13. **자동 편성 후보 개수**
    - 100개 요청 제한을 제거했다.
    - 기본 정책은 서버 전체 분반 조회이며, CP-SAT 탐색 자체는 작업당 10초로 제한한다.
14. **공강 시간 지정**
    - 6번의 `blockedTimes`를 공강 고정 계약으로 사용한다.
15. **Google 로그인**
    - 서버 주도 OIDC Authorization Code 흐름, Google `sub` 기반 계정 연결,
      JDBC 세션 저장, 토큰 미보관을 구현했다.
    - GCP 표준 웹 OAuth 클라이언트를 Secret Manager에 연결하고 운영 프로필
      `prod,gcp,google`을 활성화했다.
    - `/api/v1/auth/google`에서 Google 승인 화면까지 이어지는 OAuth state,
      HTTPS 백엔드 콜백, JDBC 세션 쿠키를 운영 환경에서 검증했다.
16. **OCR**
    - `POST /api/v1/completed-courses/ocr` multipart 이미지 API를 추가했다.
    - 별도 Cloud Vision 서비스가 아니라 Gemini 3.5 Flash-Lite의 이미지 입력을
      사용하며, 인라인 입력 한도에 맞춘 7MB 이하 원본을 저장하지 않고 전체
      텍스트와 행 목록만 반환한다.
    - 인식 결과는 사용자 확인·보정 후 기존 이수과목 등록 API로 저장한다.
17. **학번 수정**
    - `PATCH /api/v1/users/me`의 `studentNumber`로 수정한다.
    - 숫자 6~20자리와 중복 여부를 검증한다.
18. **P/N 과목**
    - `gradingBasis=LETTER|PASS_FAIL`, `gradeValue`를 추가했다.
    - P/N도 `credits`에는 실제 인정 학점을 저장하고 `gradeValue=P|N`으로 표현한다.
19. **튜토리얼 완료**
    - 사용자 프로필에 `tutorialCompleted`를 추가해 모든 기기에서 동기화한다.

## 검증 경계

- 학사 분류는 공식 원본에 존재하는 값만 제공한다.
- OCR 결과와 최종 졸업사정은 자동 확정하지 않고 사용자 확인 또는 학교 공식
  판정을 거쳐야 한다.
- 실제 Google 사용자 선택·동의와 최초 로컬 계정 생성은 사용자 상호작용이 필요한
  종단 구간이며, 서버 설정 검증은 Google 승인 화면 진입과 콜백 일치까지 수행했다.
