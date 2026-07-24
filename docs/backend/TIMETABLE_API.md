# 시간표 API

시간표 API는 로그인한 사용자가 자신의 시간표만 관리하도록 제한합니다. 모든 경로는
세션 인증이 필요하며 상태 변경 요청은 CSRF 헤더가 필요합니다. 성공 응답은 공통
envelope 없이 DTO를 본문에 직접 반환합니다.

## 엔드포인트

| Method | Path | 성공 상태 | 설명 |
|---|---|---:|---|
| POST | `/api/v1/timetables` | 201 | 시간표 생성 |
| GET | `/api/v1/timetables` | 200 | 내 시간표 목록 |
| GET | `/api/v1/timetables/{timetableId}` | 200 | 시간표 상세 |
| PATCH | `/api/v1/timetables/{timetableId}` | 200 | 시간표 이름 변경 |
| PATCH | `/api/v1/timetables/{timetableId}/sections` | 200 | 분반 구성 전체 교체 |
| POST | `/api/v1/timetables/{timetableId}/sections` | 201 | 분반 하나 추가 |
| DELETE | `/api/v1/timetables/{timetableId}/sections/{timetableCourseId}` | 200 | 분반 제거 |
| DELETE | `/api/v1/timetables/{timetableId}` | 204 | 시간표 삭제 |

## 시간표 생성

```json
{
  "name": "2026년 1학기",
  "semesterId": "2026-1",
  "sections": [
    {
      "courseCode": "CSE101",
      "sectionCode": "01"
    }
  ]
}
```

- `name`: 필수, 최대 120자
- `semesterId`: 필수, 최대 20자
- `sections`: 생략 가능, 학사 DB에 존재하는 과목·분반 키 배열

클라이언트가 과목명, 교수, 학점, 수업시간을 전달하지 않습니다. 서버가
`semesterId + courseCode + sectionCode`로 학사 DB에서 조회합니다.

## 상세 응답

```json
{
  "id": 12,
  "userId": "3c8fb145-a10f-4df8-818a-a213ef8b3fc5",
  "name": "2026년 1학기",
  "semesterId": "2026-1",
  "totalCredits": 3.0,
  "sections": [
    {
      "id": 25,
      "semesterId": "2026-1",
      "courseCode": "CSE101",
      "sectionCode": "01",
      "courseName": "컴퓨터개론",
      "professorName": "홍교수",
      "credits": 3.0,
      "meetings": [
        {
          "dayOfWeek": "MONDAY",
          "startTime": "09:00:00",
          "endTime": "10:15:00"
        }
      ]
    }
  ],
  "freeTimes": [],
  "createdAt": "2026-07-24T04:00:00Z",
  "updatedAt": "2026-07-24T04:00:00Z"
}
```

- `sections[].id`는 시간표에 담긴 항목의 `timetableCourseId`입니다. 분반 제거 경로에
  이 값을 사용합니다.
- `freeTimes`는 같은 요일의 수업과 수업 사이 공강이며
  `dayOfWeek`, `startTime`, `endTime`, `durationMinutes`를 반환합니다.
- 목록 응답은 `id`, `name`, `semesterId`, `totalCredits`, `sectionCount`만 반환합니다.

## 수정 요청

이름 변경:

```json
{
  "name": "전공 중심 시간표"
}
```

분반 전체 교체:

```json
{
  "sections": [
    { "courseCode": "CSE101", "sectionCode": "02" },
    { "courseCode": "MAT201", "sectionCode": "01" }
  ]
}
```

분반 하나 추가:

```json
{
  "courseCode": "ENG101",
  "sectionCode": "03"
}
```

## 서버 검증

- 요청한 학기의 학사 DB에 없는 분반은 거부
- 같은 시간표에 동일 과목을 중복 추가하면 거부
- 수업시간이 겹치는 분반을 함께 추가하면 `409 Conflict`
- 다른 사용자의 시간표 조회·수정·삭제는 거부
- 총 학점과 공강 시간은 서버가 분반 기준 데이터로 다시 계산

## 현재 미구현 범위

- 다른 분반으로 교체하는 전용 API
- 충돌 없는 대체 분반 추천
- 시간표 복사
- 이전 학기 시간표 조회
- 공유 링크 생성·공유 시간표 조회
