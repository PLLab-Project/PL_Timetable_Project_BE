# 프론트엔드 API 연동 안내

이 문서는 현재 구현된 백엔드의 프론트 연동 경계를 요약합니다. 세부 요청·응답 필드는
각 도메인 문서와 Java DTO가 기준입니다.

## 연결 기본값

- API prefix: `/api/v1`
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
- `/reviews/**`의 작성·내 목록·수정·삭제
- `/completed-courses/**`
- `/graduation/evaluation`, `/graduation/me/evaluation`
- `/timetables/**`
- `/optimizations/**`

인증이 없거나 세션이 만료되면 HTTP `401`과
`AUTH_SESSION_EXPIRED`가 반환됩니다.

## 도메인별 명세

- [학과·학기·강의·리뷰](ACADEMIC_API.md)
- [이수과목](COMPLETED_COURSE_API.md)
- [졸업요건](GRADUATION_API.md)

시간표와 자동 편성은 각각 `timetable`과 `optimization` 패키지의 Controller·DTO가
현재 계약입니다. 해당 영역의 별도 프론트 명세 문서는 아직 작성되지 않았습니다.

## 현재 응답 형식 주의

인증 API 성공 응답은 다음 envelope를 사용합니다.

```json
{
  "code": "SUCCESS",
  "message": "요청을 성공적으로 처리했습니다.",
  "data": {}
}
```

학사·리뷰·이수과목·졸업요건·시간표·자동 편성의 성공 응답은 현재 DTO를 본문에 직접
반환합니다. 예외 응답도 두 공통 패키지의 형식이 공존합니다. 따라서 프론트 최종 연동
전에 백엔드 팀이 성공 envelope와 오류 스키마를 하나로 확정해야 합니다.

## 백엔드 인수 완료 조건

프론트 팀에 최종 전달하기 전 다음 항목을 팀 공통으로 완료해야 합니다.

1. 소셜 로그인 여부와 최종 인증 흐름 확정
2. 성공·오류 응답 형식과 오류 코드 통일
3. 시간표·자동 편성 명세 문서 추가
4. OpenAPI 또는 동등한 기계 판독 명세 생성
5. 프론트 개발 origin과 운영 환경 변수 확정
6. 전체 PostgreSQL 통합 테스트 통과

이후 화면 상태 관리와 API 호출 연결은 프론트 팀이 담당하고, 백엔드 팀은 연동 과정에서
발견되는 계약 불일치와 서버 결함을 수정합니다.
