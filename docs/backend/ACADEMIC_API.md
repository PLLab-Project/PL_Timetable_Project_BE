# Academic catalog API

학사 조회 API는 인증 없이 읽을 수 있는 기준 데이터 API입니다. DB 원본은
`academic_units`, `semesters`, `courses`, `sections`, `sessions`,
`section_academic_units`입니다.

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
- `sort`: `NAME_ASC`, `RATING_DESC`, `POPULARITY_DESC`
- `page`, `size`: 0부터 시작, 기본 20, 최대 100

`RATING_DESC`는 해당 학기 전체 리뷰 평균과 최소 표본 5개를 사용하는 베이지안 보정
점수입니다. `POPULARITY_DESC`는 리뷰 수를 우선하고 보정 평점을 보조 기준으로 사용합니다.
리뷰가 없는 강의의 평점과 보정 평점은 `null`, 리뷰 수는 0입니다.

분반 시간은 DB의 자정 기준 분을 API 경계에서 `DayOfWeek`와 `LocalTime`으로 변환합니다.
수업시간 미정 분반은 `timeToBeAnnounced=true`이며 세션 배열이 비어 있을 수 있습니다.
