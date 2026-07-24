# 백엔드 API 명세

프론트엔드가 현재 구현된 API를 연동할 때 사용하는 문서 인덱스입니다. 정확한 요청·응답
스키마는 실행 중인 서버가 생성하는 OpenAPI 명세를 기준으로 하고, 아래 Markdown 문서는
인증 흐름과 도메인 규칙을 설명합니다.

## 실행 중인 명세

팀 테스트 서버:

- 기본 API 문서(Scalar): `https://timetable-api.kdhoon.me/`
- Scalar 직접 주소: `https://timetable-api.kdhoon.me/scalar`
- 기존 Swagger UI: `https://timetable-api.kdhoon.me/swagger-ui.html`
- OpenAPI JSON: `https://timetable-api.kdhoon.me/v3/api-docs`
- OpenAPI YAML: `https://timetable-api.kdhoon.me/v3/api-docs.yaml`

로컬 서버를 `8080` 포트에서 실행했을 때:

- 기본 API 문서(Scalar): `http://localhost:8080/`
- Scalar 직접 주소: `http://localhost:8080/scalar`
- 기존 Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

자세한 사용법과 운영 환경 비활성화 방법은 [OpenAPI·API 문서](OPENAPI.md)를 확인합니다.

## 도메인 문서

| 영역 | 문서 |
|---|---|
| 인증·사용자·개인정보 동의 | [AUTH_USER_API.md](AUTH_USER_API.md) |
| 학과·학기·강의·리뷰 | [ACADEMIC_API.md](ACADEMIC_API.md) |
| 이수과목 | [COMPLETED_COURSE_API.md](COMPLETED_COURSE_API.md) |
| 졸업요건 조회·판정 | [GRADUATION_API.md](GRADUATION_API.md) |
| 시간표 | [TIMETABLE_API.md](TIMETABLE_API.md) |
| 자동 편성 | [OPTIMIZATION_API.md](OPTIMIZATION_API.md) |
| 프론트 연동 공통사항 | [루트 FRONTEND.md](../../FRONTEND.md) |
| 실제 HTTP 스모크 테스트 | [LOCAL_API_SMOKE_TEST.md](LOCAL_API_SMOKE_TEST.md) |

## 공통 인증 규칙

- 세션 쿠키: `JSESSIONID`
- 브라우저 요청: `credentials: "include"` 사용
- CSRF 토큰: `GET /api/v1/auth/csrf`의 `data.token`
- 인증된 상태 변경 요청: 토큰을 `X-XSRF-TOKEN` 헤더에 포함
- CSRF·OTP 요청·검증, 공개 학사 조회, Scalar·Swagger·OpenAPI 명세는 인증 없이 접근 가능
- 다른 사용자의 소유 데이터에는 접근할 수 없음

## 공통 응답 계약

모든 성공 응답과 오류 응답은 다음 envelope를 사용합니다.

```json
{
  "code": "SUCCESS",
  "message": "요청을 성공적으로 처리했습니다.",
  "data": {}
}
```

- 성공 데이터는 `data`에서 읽습니다.
- 목록도 `data`가 배열 또는 페이지 객체입니다.
- 삭제·취소 성공은 HTTP `200`, `code=SUCCESS`, `data=null`입니다.
- 생성 성공은 HTTP `201`, 나머지 성공은 HTTP `200`입니다.
- 오류는 HTTP 상태와 도메인 오류 `code`를 함께 확인하며 `data=null`입니다.

OpenAPI에는 성공 envelope, 실제 요청 예제, 파라미터 설명과 공통 400·401·403·500
오류가 표시됩니다. 각 API에서 발생 가능한 404·409·422·429·503 업무 오류도 해당
작업에 함께 표시됩니다.

OpenAPI는 Controller·DTO와 문서 카탈로그에서 서버 실행 시 자동 생성됩니다. 코드가
Git `main`에 병합되기만 해서는 외부 문서가 바뀌지 않으며, 최신 JAR 또는 Docker 이미지를
테스트 서버에 배포하고 재시작해야 `/v3/api-docs`도 최신화됩니다.

## 아직 구현되지 않은 주요 기능

- 소셜 로그인
- 졸업판정용 `admissionYear`, `studentType`, `programPath` 사용자 프로필 입력
- 시간표 복사, 공유 링크, 이전 학기 조회, 대체 분반 추천
- OCR

이 목록은 현재 구현 범위를 구분하기 위한 것이며, 구현된 API의 동작 여부와는 별개입니다.
