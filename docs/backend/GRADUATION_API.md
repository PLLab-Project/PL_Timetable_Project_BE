# 졸업요건 API

졸업요건 API는 정규화된 졸업요건 기준 테이블과 사용자의
`completed_courses`를 결합해 규칙 조회와 개인 판정을 제공합니다.

## 졸업요건 규칙 조회

`GET /api/v1/graduation/rules`

인증 없이 조회할 수 있습니다.

필수 쿼리 파라미터:

- `admissionYear`: 입학연도 (`1900..2100`)
- `academicUnit`: 정규 학과 코드·학과명·등록된 별칭
- `studentType`: 원천 데이터의 학생 구분
- `programPath`: `ADVANCED_MAJOR`, `DOUBLE_MAJOR`, `MINOR`, `MICRO_MAJOR`

응답에는 총학점·전공·교양 최소학점, 교양 영역, 전공·교양 필수과목,
공식 근거, 정규화 경고, 자동 판정 불가 항목이 포함됩니다.

## 개인 졸업요건 판정

- `GET /api/v1/graduation/evaluation`
- 별칭: `GET /api/v1/graduation/me/evaluation`

세션 인증이 필요합니다. 입학연도·학과·학생 구분·전공 방식은 요청값으로
받지 않고 로그인 사용자의 `student_profiles`에서 읽습니다.

선택 파라미터 `semesterId`는 부족 과목 추천에 사용할 개설 학기를 지정합니다.
생략하면 최신 활성 학기를 사용합니다.

판정에는 `completed_courses.status = 'COMPLETED'`인 과목만 반영합니다.
응답에는 다음 정보가 포함됩니다.

- 총학점·전공기초·전공필수·전공선택·교양 이수학점
- 부족 학점·교양 영역·필수과목
- 지정 학기에 개설된 부족 과목 후보
- 공식 근거와 원천 경고
- 상담·자격 증빙·예외 승인 등 자동 판정 불가 항목

현재 강의 카탈로그에는 교양 영역 정보가 없으므로, 교양 영역 부족분에 대한
과목 추천은 자동화하지 않습니다. 추가 전공 귀속과 개인별 증빙도 추측하지 않고
자동 판정 불가 항목으로 반환합니다.
