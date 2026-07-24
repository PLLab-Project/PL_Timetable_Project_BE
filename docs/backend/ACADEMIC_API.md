# Academic catalog API

학사 조회 API는 인증 없이 읽을 수 있는 기준 데이터 API입니다. DB 원본은
`academic_units`, `semesters`, `courses`, `sections`, `sessions`,
`section_academic_units`입니다.

모든 성공 응답은 공통 `ApiResponse<T>` envelope를 사용하며 아래에서 설명하는 목록,
페이지, 상세 객체는 `data`에 들어갑니다.

## 학과·전공

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/departments` | 공식 학과·전공 목록 |
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
| GET | `/api/v1/courses/{semesterId}/{courseCode}` | 강의 상세 |
| GET | `/api/v1/courses/{semesterId}/{courseCode}/sections` | 분반 목록 |
| GET | `/api/v1/courses/{semesterId}/{courseCode}/sections/{sectionCode}` | 분반 상세 |

강의 목록 파라미터:

- `semesterId`: 필수
- `query`: 과목코드·과목명·교수명 부분 검색
- `category`: 이수구분 정확히 일치
- `academicUnitCode`: 학과·전공 코드
- `professor`: 교수명 부분 검색
- `credits`: 학점 정확히 일치
- `day`: `월`~`일` 또는 `MONDAY`~`SUNDAY`
- `sort`: `NAME_ASC`, `NAME_DESC`, `REVIEW_COUNT_DESC`, `RATING_DESC`,
  `POPULARITY_DESC`
- `page`, `size`: 0부터 시작, 기본 20, 최대 100

`RATING_DESC`는 해당 학기 전체 리뷰 평균과 최소 표본 5개를 사용하는 베이지안 보정
점수입니다. `REVIEW_COUNT_DESC`는 리뷰 수만 우선하며, `POPULARITY_DESC`는 리뷰 수를
우선하고 보정 평점을 보조 기준으로 사용합니다. 리뷰가 없는 강의의 평점과 보정 평점은
`null`, 리뷰 수는 0입니다.

현재 강의 원본의 `courses`, `sections`에는 수강 대상 학년 필드가 없으므로 강의 검색은
학년 필터를 제공하지 않습니다. 졸업요건의 권장 학년은 현재 개설 강의의 수강 대상
학년과 동일한 데이터 계약이 아닙니다.

분반 시간은 DB의 자정 기준 분을 API 경계에서 `DayOfWeek`와 `LocalTime`으로 변환합니다.
수업시간 미정 분반은 `timeToBeAnnounced=true`이며 세션 배열이 비어 있을 수 있습니다.

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
