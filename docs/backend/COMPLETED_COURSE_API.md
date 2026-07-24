# 이수과목 API

`completed_courses` DB 계약을 사용하는 사용자 전용 API다. 모든 엔드포인트는
세션 인증이 필요하며 서버는 요청 본문의 사용자 식별자를 받지 않고
`AuthenticatedUser.userId()`만 사용한다. `POST`, `PATCH`, `DELETE` 요청에는
기존 보안 정책에 따라 CSRF 토큰도 필요하다.

기본 경로는 `/api/v1/completed-courses`다.
모든 성공 응답은 공통 `ApiResponse<T>` envelope를 사용하며 아래 예시의 업무 데이터는
`data`에 들어간다. 삭제 성공은 HTTP `200`과 `data=null`이다.

## 상태와 입력 출처

- `status`: `COMPLETED`, `IN_PROGRESS`, `PLANNED`, `FAILED`, `WITHDRAWN`
- `inputSource`: `MANUAL`, `TIMETABLE`, `OCR`, `IMPORT`
- 직접 등록 API는 `MANUAL`, 시간표 가져오기는 `TIMETABLE`을 서버가 설정한다.
- 시간표에서 가져온 과목은 먼저 `IN_PROGRESS`가 된다.

## 직접 등록

`POST /api/v1/completed-courses`

```json
{
  "courseCode": "CSE100",
  "courseName": "자료구조",
  "credits": 3.00,
  "category": "전공필수",
  "area": "전공핵심",
  "semester": "2026-1",
  "status": "IN_PROGRESS"
}
```

`courseCode`, `area`, `semester`는 선택값이다. 학점은 `0` 이상,
소수점 둘째 자리까지 허용한다.

## 조회·수정·삭제

- `GET /api/v1/completed-courses`
  - 선택 필터: `status`, `semester`
- `GET /api/v1/completed-courses/{completedCourseId}`
- `PATCH /api/v1/completed-courses/{completedCourseId}`
  - 등록 요청과 같은 필드 중 변경할 필드만 전송한다.
- `DELETE /api/v1/completed-courses/{completedCourseId}`

타인의 식별자를 조회하거나 변경하려 하면 존재 여부를 노출하지 않고
`COMPLETED_COURSE_NOT_FOUND`를 반환한다.

## 학점 요약

`GET /api/v1/completed-courses/summary`

전체, 이수 완료, 수강 중 학점과 전공·교양 분류(`category`), 세부 영역(`area`),
상태별 학점 합계를 반환한다.

```json
{
  "code": "SUCCESS",
  "message": "요청을 성공적으로 처리했습니다.",
  "data": {
    "totalCredits": 5.00,
    "completedCredits": 2.00,
    "inProgressCredits": 3.00,
    "creditsByCategory": {
      "전공선택": 3.00,
      "교양필수": 2.00
    },
    "creditsByArea": {
      "전공심화": 3.00,
      "의사소통": 2.00
    },
    "creditsByStatus": {
      "COMPLETED": 2.00,
      "IN_PROGRESS": 3.00
    }
  }
}
```

## 시간표 가져오기

`POST /api/v1/completed-courses/imports/timetables/{timetableId}`

인증 사용자가 소유한 시간표의 모든 분반을 가져온다. 과목명·학점·분반은
시간표 스냅샷에서, 분류는 같은 학기의 정규 `courses` 데이터에서 읽는다.
`area`는 원본 시간표에 없으므로 `null`이며 이후 수정 API로 지정할 수 있다.
동일 사용자·학기·과목·분반을 같은 `TIMETABLE` 출처로 다시 가져오면
중복 생성하지 않고 `skippedCount`에 포함한다.

```json
{
  "code": "SUCCESS",
  "message": "요청을 성공적으로 처리했습니다.",
  "data": {
    "timetableId": 10,
    "importedCount": 1,
    "skippedCount": 0,
    "records": [
      {
        "courseCode": "CSE300",
        "sectionCode": "01",
        "status": "IN_PROGRESS",
        "inputSource": "TIMETABLE",
        "sourceSnapshot": {
          "timetableId": 10,
          "timetableCourseId": 20,
          "professorName": "김교수"
        }
      }
    ]
  }
}
```

## 수강 완료 전환

`POST /api/v1/completed-courses/{completedCourseId}/complete`

`IN_PROGRESS`인 사용자 소유 과목을 `COMPLETED`로 전환한다. 다른 상태에서
호출하면 HTTP `409`와 `COMPLETED_COURSE_INVALID_STATUS_TRANSITION`을 반환한다.
