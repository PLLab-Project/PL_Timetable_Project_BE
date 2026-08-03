# 시간표 자동 편성 API

자동 편성은 학사 DB의 정식 분반을 후보로 받아 OR-Tools CP-SAT으로 조건을 만족하는
시간표를 탐색합니다. 모든 API는 세션 인증이 필요하고 생성·취소 요청은 CSRF 헤더가
필요합니다. 성공 응답은 공통 `ApiResponse<T>` envelope를 사용하며 작업 DTO는
`data`에 들어갑니다.

## 엔드포인트

| Method | Path | 성공 상태 | 설명 |
|---|---|---:|---|
| POST | `/api/v1/optimizations` | 201 | 비동기 자동 편성 작업 생성 |
| GET | `/api/v1/optimizations/{jobId}` | 200 | 작업 상태와 결과 조회 |
| DELETE | `/api/v1/optimizations/{jobId}` | 200 | 완료 전 작업 취소 (`data=null`) |
| POST | `/api/v1/optimizations/{jobId}/results/{rank}/apply` | 200 | 결과를 대상 시간표에 저장 |

완료된 작업을 취소하면 `409 Conflict`가 반환됩니다.

## 작업 생성

```json
{
  "timetableId": 12,
  "minCredits": 12.0,
  "maxCredits": 18.0,
  "targetCredits": 15.0,
  "excludedDays": ["FRIDAY"],
  "availableTimes": [
    { "startTime": "09:00:00", "endTime": "12:00:00" },
    { "startTime": "13:00:00", "endTime": "18:00:00" }
  ],
  "blockedTimes": [
    {
      "dayOfWeek": "WEDNESDAY",
      "startTime": "15:00:00",
      "endTime": "17:00:00"
    }
  ],
  "lunchTime": {
    "startTime": "12:00:00",
    "endTime": "13:00:00"
  },
  "maxDailyClassMinutes": 360,
  "candidateCourses": [],
  "requiredCourses": [
    {
      "courseCode": "CSE101",
      "sectionCode": "01",
      "required": true
    }
  ]
}
```

## 입력 규칙

- `timetableId`: 로그인 사용자가 소유한 시간표
- `minCredits <= targetCredits <= maxCredits`
- `excludedDays`: `MONDAY`~`SUNDAY`
- `availableTimes`: 하나 이상의 수강 가능 시간 범위. 기존 `availableTime`도 호환
- `blockedTimes`: 강의를 배치하지 않을 요일별 공강 고정 범위
- `lunchTime`: 시작 시간이 종료 시간보다 빨라야 함
- `maxDailyClassMinutes`: 1 이상
- `candidateCourses`: 선택값. 비우면 서버가 학기의 전체 분반을 직접 조회
- `requiredCourses`: 서버 직접 조회 시 반드시 포함할 분반
- 후보는 시간표 학기와 동일한 학사 DB에 존재해야 함
- 명시적 선택 후보 중 수업시간 미정 분반은 자동으로 제외
- 수업시간 미정 분반을 필수로 지정하면 요청을 거절하며, 제외 후 후보가 없을 때도
  자동편성을 시작하지 않음
- `required=true`인 후보는 반드시 배치되며 서로 충돌하거나 제약을 위반하면 요청이 실패

후보 과목명·교수·학점·시간은 클라이언트 값을 받지 않고 학사 DB에서 조회합니다.

## 상태 조회

작업 상태:

- `PENDING`: 생성됨
- `PROCESSING`: 탐색 중
- `SUCCESS`: 결과 생성 완료
- `FAILED`: 조건 또는 실행 오류로 실패
- `TIMEOUT`: 제한 시간 안에 결과를 찾지 못함
- `CANCELLED`: 사용자 취소

```json
{
  "code": "SUCCESS",
  "message": "요청을 성공적으로 처리했습니다.",
  "data": {
    "id": 40,
    "userId": "3c8fb145-a10f-4df8-818a-a213ef8b3fc5",
    "timetableId": 12,
    "semesterId": "2026-1",
    "status": "SUCCESS",
    "failureReason": null,
    "results": [
      {
        "rank": 1,
        "sections": [],
        "attendanceDays": 4,
        "totalCredits": 15.0,
        "totalFreeMinutes": 120,
        "score": 155.5
      }
    ],
    "createdAt": "2026-07-24T04:00:00Z"
  }
}
```

한 분반에 여러 수업시간이 있으면 `results[].sections`에 같은 과목·분반 키의 시간 슬롯이
여러 행으로 나타날 수 있습니다.

성공한 결과를 시간표에 확정하려면 rank를 골라 `apply` API를 호출한다. 서버는 결과의
분반 키를 학사 DB에서 다시 조회하고 작업의 `timetableId` 분반 구성을 전체 교체한다.

## 탐색 특성

- CP-SAT 탐색 제한 시간: 작업당 10초
- 서버 CPU에 따라 탐색 worker를 최대 8개까지 사용
- 결과: 점수 순으로 최대 3개
- 이미 선택된 결과와 과목 구성이 70% 이상 겹치는 조합은 제외하여 다양성 확보
- 시간 충돌과 학점 범위는 하드 제약
- 목표 학점 차이, 등교일, 공강, 점심시간, 하루 수업량은 점수에 반영

클라이언트 후보 100개 제한은 제거했다. 서버 전체 조회에서도 CP-SAT 탐색은 작업당
10초로 제한되며, 명시적 후보를 보내면 그 목록만 사용한다.
