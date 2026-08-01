# Academic catalog API

학사 조회 API는 인증 없이 읽을 수 있는 기준 데이터 API입니다. DB 원본은
`academic_units`, `semesters`, `courses`, `sections`, `sessions`,
`session_rooms`, `section_academic_units`, `section_classification_contexts`입니다.

모든 성공 응답은 공통 `ApiResponse<T>` envelope를 사용하며 아래에서 설명하는 목록,
페이지, 상세 객체는 `data`에 들어갑니다.

## 학과·전공

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/departments` | 공식 학과·전공 목록 |
| GET | `/api/v1/departments/colleges` | 단과대 코드·이름·현재 학과 수 |
| GET | `/api/v1/departments/{code}` | 학과·전공 상세와 연도별 별칭 |

목록 파라미터:

- `query`: 학과명 또는 코드 부분 검색
- `collegeCode`: 단과대 코드
- `currentOnly`: 현재 데이터셋 등장 여부, 기본 `true`
- `page`: 0부터 시작
- `size`: 기본 20, 최대 100

요건 문서에서만 파생된 `REQUIREMENT_DERIVED` 코드는 학과 목록에서 제외합니다.

## 학기

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/semesters` | 학기 목록 |
| GET | `/api/v1/semesters/{semesterId}` | 학기 상세 |
| GET | `/api/v1/semesters/{semesterId}/version` | 데이터 버전과 체크섬 |

학기 목록은 `activeOnly=true`가 기본값입니다.

## 강의·분반

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/courses` | 강의 검색·필터·정렬 |
| GET | `/api/v1/sections` | 메인 시간표 화면용 분반 카드 검색·필터·정렬 |
| GET | `/api/v1/courses/{semesterId}/{courseCode}` | 강의 상세 |
| GET | `/api/v1/courses/{semesterId}/{courseCode}/sections` | 분반 목록 |
| GET | `/api/v1/courses/{semesterId}/{courseCode}/sections/{sectionCode}` | 분반 상세 |

강의 목록 파라미터:

- `semesterId`: 필수
- `query`: 과목코드·과목명·교수명 부분 검색
- `category`: 강의 편성 분류 다중 선택. `디지털리터러시` 별칭 지원
- `academicUnitCode`: 학과·전공 코드 다중 선택
- `collegeCode`: 단과대 코드 다중 선택
- `professor`: 교수명 부분 검색
- `credits`: 학점 정확히 일치
- `day`: `월`~`일` 또는 `MONDAY`~`SUNDAY`
- `sort`: `DEFAULT`, `NAME_ASC`, `NAME_DESC`, `REVIEW_COUNT_DESC`, `RATING_DESC`,
  `POPULARITY_DESC`
- `page`, `size`: 0부터 시작, 기본 20, 최대 100

메인 시간표 화면의 하단 카드 목록은 과목별 분반 API를 반복 호출하지 말고
`GET /api/v1/sections`를 사용합니다. 이 API는 위 조건에 더해 다음 분반 조건을
지원합니다.

- `completionCategory`: 학과 문맥별 이수구분 다중 선택 (`전필`, `전선`, `교필`,
  `교선`, `교직`, `전기`, `일선`)
- `targetGrade`: `1`~`4` 또는 `1학년`~`4학년` 다중 선택
- `preferredAcademicUnitCode`: 공개 호출에서 우선 정렬할 학과. 로그인 시 프로필 사용

다중 파라미터는 같은 이름을 반복하거나 쉼표로 구분한다. `DEFAULT`는 분반의 원본
카탈로그 페이지·행 순이고 `NAME_ASC`는 별도 이름순이다. `preferredAcademicUnitCode`가
있어도 선택한 `sort`의 과목 순서는 바뀌지 않는다. 같은 `courseCode`의 분반 안에서만
비고(`notes`)에 해당 학과명 또는 등록된 학과 별칭이 있는 분반을 먼저 반환한다.
일치하는 비고가 없으면 기존 분반 순서를 유지한다. 검색·필터 결과에도 같은 규칙을 쓴다.

응답의 `items`는 과목명·과목코드·분반·교수·학점·대상 학년·`notes` 비고와
카드 표시용 `completionCategory`, `sessions` 전체 수업시간·강의실,
`classifications` 학과별 전체 이수구분 문맥을 포함합니다. `academicUnitCode` 필터를
사용하면 `completionCategory`는 그 학과 문맥을 우선합니다.
무한 스크롤은 `page=0&size=20`부터 시작해 `page`를 증가시키고, `totalPages`에
도달하면 중단합니다. 한 번에 전체 분반을 내려받지 않습니다.

`RATING_DESC`는 해당 학기 전체 리뷰 평균과 최소 표본 5개를 사용하는 베이지안 보정
점수입니다. `REVIEW_COUNT_DESC`는 리뷰 수만 우선하며, `POPULARITY_DESC`는 리뷰 수를
우선하고 보정 평점을 보조 기준으로 사용합니다. 리뷰가 없는 강의의 평점과 보정 평점은
`null`, 리뷰 수는 0입니다.

2026-2 공식 분반은 `targetGrade`, `capacity`, `notes`, `rawLocation`을 제공합니다.
2026-1에는 원천에 없던 값이므로 `null`일 수 있습니다. 과목 단위 `/api/v1/courses`는
학년 필터를 제공하지 않고, 분반 단위 `/api/v1/sections`만 `targetGrade`를
지원합니다. 졸업요건의 권장 학년은 현재 개설 강의의 수강 대상 학년과 동일한 데이터
계약이 아닙니다.

분반 시간은 DB의 자정 기준 분을 API 경계에서 `DayOfWeek`와 `LocalTime`으로 변환합니다.
수업시간 미정 분반은 `timeToBeAnnounced=true`이며 세션 배열이 비어 있을 수 있습니다.
`sessions[].roomCode`, `roomLabel`, `buildingName`은 기존 클라이언트 호환용 대표
강의실이고, `sessions[].rooms`가 같은 시간에 사용하는 전체 강의실의 순서 있는
목록입니다.

분반 상세의 `classifications`는 같은 분반이 서로 다른 학과·융합전공 표에 등장할 때
각 문맥의 `completionCategory`, `targetGrade`, `academicUnitCode`, 원본 페이지를
그대로 제공합니다. 따라서 과목의 단일 `category`만으로 학과별 이수구분을 판정하면
안 됩니다.

## 강의 리뷰

공개 리뷰 응답에는 작성자 사용자 ID나 이메일을 포함하지 않습니다. 목록은
`createdAt DESC, id DESC` 순서이며 `page`, `size` 규칙은 강의 목록과 같습니다.

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/v1/courses/reviews` | 불필요 | 전체 리뷰 목록 |
| GET | `/api/v1/courses/reviews/{courseCode}` | 불필요 | 과목별 리뷰 목록 |
| GET | `/api/v1/courses/reviews/{courseCode}/professors/{professor}` | 불필요 | 과목·교수별 리뷰 목록 |
| POST | `/api/v1/reviews` | 필요 | 리뷰 작성 |
| GET | `/api/v1/reviews/me` | 필요 | 내 리뷰 목록 |
| PATCH | `/api/v1/reviews/{reviewId}` | 필요 | 내 리뷰 별점·내용 수정 |
| DELETE | `/api/v1/reviews/{reviewId}` | 필요 | 내 리뷰 삭제 |

공개 목록과 내 리뷰 목록은 선택적으로 `semesterId`를 받습니다. 작성 요청은
`semesterId`, `courseCode`, 선택 `professor`, 1~5의 `rating`, 공백이 아닌
`content`를 사용합니다. 과목명은 서버가 해당 학기 강의 원본에서 결정하며, 교수를
지정하면 해당 학기·과목의 분반 교수와 일치해야 합니다.

한 사용자는 `(courseCode, professor, semesterId)` 조합마다 하나의 리뷰만 작성할 수
있습니다. `professor=null`도 하나의 동일 조합으로 처리합니다. 수정·삭제는 인증된
작성자에게만 허용하며, 다른 사용자의 리뷰 ID는 존재 여부를 노출하지 않고 404로
응답합니다.
