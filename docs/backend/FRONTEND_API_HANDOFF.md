# 프론트엔드 API 연동 안내

이 문서는 현재 구현된 백엔드의 프론트 연동 경계를 요약합니다. 세부 요청·응답 필드는
각 도메인 문서와 Java DTO가 기준입니다.

## 연결 기본값

- API prefix: `/api/v1`
- 팀 테스트 서버: `https://timetable-api.kdhoon.me`
- 기본 API 문서(Scalar): `https://timetable-api.kdhoon.me/`
- 기존 Swagger UI: `https://timetable-api.kdhoon.me/swagger-ui.html`
- 로컬 백엔드: Spring Boot 기본 포트 `8080`
- 기본 허용 프론트 origin: `http://localhost:5173`
- 세션 인증: 브라우저 요청에 쿠키 포함 (`credentials: "include"`)
- 상태 변경 요청: `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 전송
- OTP 요청·검증 경로는 CSRF 검사에서 제외

## 인증 경계

인증 없이 가능한 GET:

- `/departments/**`
- `/semesters/**`
- `/courses/**`
- `/graduation/rules`

인증이 필요한 영역:

- `/auth/session`, `/auth/logout`
- `/users/me/**`
- `/reviews/**`의 작성·내 목록·수정·삭제
- `/completed-courses/**`
- `/graduation/evaluation`, `/graduation/me/evaluation`
- `/timetables/**`
- `/optimizations/**`

인증이 없거나 세션이 만료되면 HTTP `401`과
`AUTH_SESSION_EXPIRED`가 반환됩니다.

## 도메인별 명세

- [전체 API 문서 인덱스](API_REFERENCE.md)
- [인증·사용자](AUTH_USER_API.md)
- [학과·학기·강의·리뷰](ACADEMIC_API.md)
- [이수과목](COMPLETED_COURSE_API.md)
- [졸업요건](GRADUATION_API.md)
- [시간표](TIMETABLE_API.md)
- [자동 편성](OPTIMIZATION_API.md)
- [OpenAPI·API 문서 사용법](OPENAPI.md)
- [로컬 실사용 스모크 테스트 결과](LOCAL_API_SMOKE_TEST.md)

실행 중인 서버의 루트(`/`)와 `/scalar`에서 기본 대화형 문서를 확인할 수 있습니다.
`/swagger-ui.html`은 기존 Swagger UI를 제공하고, `/v3/api-docs`와
`/v3/api-docs.yaml`은 기계 판독 가능한 최신 스키마를 제공합니다.

## 공통 응답 형식

모든 API 성공 응답은 다음 envelope를 사용합니다.

```json
{
  "code": "SUCCESS",
  "message": "요청을 성공적으로 처리했습니다.",
  "data": {}
}
```

- 실제 도메인 응답은 항상 `data` 아래에 있습니다.
- 생성은 HTTP `201`, 나머지 성공은 HTTP `200`입니다.
- 삭제·취소 성공도 HTTP `200`과 `data=null`을 반환합니다.
- 오류도 같은 세 필드를 사용하며 `code`는 `VALIDATION_ERROR`,
  `AUTH_SESSION_EXPIRED` 같은 안정적인 식별자입니다.

## 백엔드 인수 완료 조건

프론트 팀에 최종 전달하기 전 다음 항목을 팀 공통으로 완료해야 합니다.

1. 소셜 로그인 여부와 졸업판정용 학생 프로필 입력 흐름 확정
2. 프론트 개발 origin과 운영 환경 변수 확정
3. 운영 배포 환경에서 전체 PostgreSQL 통합·인수 테스트 통과

이후 화면 상태 관리와 API 호출 연결은 프론트 팀이 담당하고, 백엔드 팀은 연동 과정에서
발견되는 계약 불일치와 서버 결함을 수정합니다.
