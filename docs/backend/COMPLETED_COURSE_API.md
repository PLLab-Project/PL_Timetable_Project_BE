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
  "gradingBasis": "LETTER",
  "gradeValue": "A+",
  "category": "전공필수",
  "area": "전공핵심",
  "semester": "2026-1",
  "status": "IN_PROGRESS"
}
```

`courseCode`, `area`, `semester`는 선택값이다. 학점은 `0` 이상,
소수점 둘째 자리까지 허용한다.

P/N 과목은 학점을 0으로 보내지 않는다. `credits`에는 실제 인정 학점을 넣고
`gradingBasis=PASS_FAIL`, `gradeValue=P` 또는 `N`으로 보낸다.

## 성적표·시간표 OCR과 강의 DB 매칭

`POST /api/v1/completed-courses/ocr`

`multipart/form-data`의 `file`에 7MB 이하 JPEG·PNG·WebP·HEIC·HEIF 이미지를 보낸다.
서버가 원본을 저장하지 않고 Gemini 3.5 Flash-Lite 비전에 직접 전달한다. 첫 번째 호출로
원문을 전사하고, 두 번째 호출은 이미지 자체와 전사문을 함께 사용해 학기·과목·교수·요일·
시간·강의실을 구조화한다. 이후 과목명, 학기, 교수, 시간, 강의실을 실제 학사 DB와 비교해
과목 코드와 분반 후보를 결정적으로 반환한다.
`extractedText`, 빈 줄을 제거한 `lines`도 그대로 유지하므로 사용자가 구조화 결과와
원문을 대조할 수 있다.

현재 프론트 입력 폼과의 호환을 위해 과목이 한 개의 DB 과목 코드로 확인되면 분반이
미확정이어도 `recognizedCourses[].courseName`, `credits`, `category`, `semester`를 DB
정규값으로 채운다. `credits`는 카탈로그 학점이며 `category`는 로그인 사용자의 학과
문맥에 해당하는 전필·전선 등을 우선한다. 정확한 분반은 `matchedSection`, 후보는
`matchCandidates`에 그대로 유지하므로 프론트가 추후 분반 확인 UI를 추가할 수 있다.

```json
{
  "extractedText": "2026-1 전공선택 자료구조 3 A+",
  "lines": ["2026-1 전공선택 자료구조 3 A+"],
  "documentType": "TIMETABLE",
  "recognizedSemester": "2026-1",
  "resolvedSemester": "2026-1",
  "recognizedCourses": [
    {
      "courseName": "자료구조",
      "credits": 3.0,
      "gradingBasis": "LETTER",
      "category": "전공선택",
      "area": null,
      "semester": "2026-1",
      "confidence": 0.94,
      "professor": null,
      "meetings": [
        {
          "dayOfWeek": "MONDAY",
          "startTime": "15:30:00",
          "endTime": "17:30:00",
          "room": "공다A411-강의실(공용)"
        }
      ],
      "matchStatus": "MATCHED",
      "matchedSection": {
        "semesterId": "2026-1",
        "courseCode": "561103",
        "sectionCode": "01",
        "courseName": "자료구조",
        "professor": "박정규",
        "category": "전공(AI융합대학/컴퓨터공학전공)",
        "completionCategory": "전선",
        "credits": 3.0,
        "matchScore": 0.80,
        "matchedEvidence": ["COURSE_NAME", "SEMESTER", "MEETINGS_2_OF_2"]
      },
      "matchCandidates": [
        {
          "semesterId": "2026-1",
          "courseCode": "561103",
          "sectionCode": "01",
          "matchScore": 0.80
        }
      ]
    }
  ],
  "requiresConfirmation": true
}
```

`recognizedSemester`는 Gemini가 이미지에서 직접 읽은 값이다. 학기 표기가 잘린 시간표는
이 값을 `null`로 유지한다. 여러 인식 과목이 한 학기에만 집중되면 실제 DB 근거로
`resolvedSemester`를 별도로 확정한다. `MATCHED`는 보이는 증거로 분반까지 확정,
`COURSE_MATCHED`는 과목 코드는 확인했지만 분반은 미확정, `AMBIGUOUS`는 여러 학기·과목
후보가 경합, `UNMATCHED`는 DB에 일치 과목이 없음을 뜻한다. 강의실 등 보이는 정보가
후보와 충돌하면 성급히 `MATCHED`로 확정하지 않는다. 학기 합의는 `TIMETABLE`에서만
교수·시간·강의실 일치 근거가 있는 여러 과목으로 계산하며, 다학기 성적표의 개별 학기를
전역 추론값으로 덮어쓰지 않는다.

구조화 JSON 생성 또는 파싱만 실패하면 OCR 요청 전체를 실패시키지 않고
`recognizedCourses=[]`로 반환하여 기존 수동 입력 흐름을 유지한다. 인식 결과를 사용자가
확인·보정한 뒤 직접 등록 API로 저장하며 OCR API는 자동 저장하지 않는다.

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
