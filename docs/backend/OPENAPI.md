# OpenAPI·API 문서 사용법

백엔드는 `springdoc-openapi`로 실행 중인 Controller와 DTO에서 OpenAPI 3 명세를
생성합니다. 프론트 연동용 기본 화면은 Scalar이며, 기존 Swagger UI도 호환성과 디버깅을
위해 함께 제공합니다.

`/v3/api-docs`의 `v3`는 OpenAPI 문서 규격 세대를 의미합니다. 서비스 API 버전은
`/api/v1`이며 OpenAPI `info.version`도 `v1`입니다.

## 팀 테스트 서버

| 용도 | URL |
|---|---|
| 기본 API 문서(Scalar) | `https://timetable-api.kdhoon.me/` |
| Scalar 직접 주소 | `https://timetable-api.kdhoon.me/scalar` |
| 기존 Swagger UI | `https://timetable-api.kdhoon.me/swagger-ui.html` |
| OpenAPI JSON | `https://timetable-api.kdhoon.me/v3/api-docs` |
| OpenAPI YAML | `https://timetable-api.kdhoon.me/v3/api-docs.yaml` |
| 서버 상태 | `https://timetable-api.kdhoon.me/api/v1/health/live` |

API 전용 호스트의 루트(`/`)는 Scalar로 이동합니다. Scalar와 Swagger UI는 같은 OpenAPI
명세를 읽으므로 요청·응답 계약은 동일합니다. Scalar는 탐색과 프론트 전달의 기본 화면,
Swagger UI는 기존 도구 호환이나 요청 시험이 필요할 때 사용할 수 있습니다.

이 서버는 Cloudflare Tunnel을 통해 HTTPS로 제공됩니다. 데이터베이스 포트는 외부에
공개하지 않습니다. 현재 인증 구현은 소셜 로그인이 아니라 OTP 세션 방식이며 테스트
서버의 OTP 전달 방식은 운영 메일 발송 설정이 확정되기 전까지 개발 설정을 사용합니다.

health 응답의 `data.commit`으로 외부 서버가 어느 Git 커밋을 실행 중인지 확인할 수
있습니다. GitHub `main`이 변경되어도 실행 서버를 다시 빌드·배포하기 전까지 외부
OpenAPI는 자동으로 바뀌지 않습니다. 배포된 애플리케이션이 시작될 때 현재 코드에서
명세를 다시 생성합니다.

## 로컬 주소

서버 기본 포트가 `8080`일 때:

| 용도 | URL |
|---|---|
| 기본 API 문서(Scalar) | `http://localhost:8080/` |
| Scalar 직접 주소 | `http://localhost:8080/scalar` |
| 기존 Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| OpenAPI YAML | `http://localhost:8080/v3/api-docs.yaml` |

위 문서 경로는 인증 없이 조회할 수 있습니다.

## 인증된 API 시험

현재 인증은 OAuth가 아니라 OTP 세션 방식입니다.

1. `/api/v1/auth/otp/request`와 `/api/v1/auth/otp/verify`를 호출합니다.
2. 브라우저가 받은 `JSESSIONID` 쿠키를 유지합니다.
3. `GET /api/v1/auth/csrf`의 `data.token`을 받습니다.
4. GET 이외의 보호 API에는 토큰을 `X-XSRF-TOKEN` 헤더로 전송합니다.
5. Scalar 또는 Swagger UI의 인증 입력란에 `csrfHeader` 값을 입력합니다.

`sessionCookie`는 브라우저가 API 서버 쿠키를 자동으로 전송하므로 보통 Swagger UI에
직접 입력할 필요가 없습니다. 프론트 애플리케이션에서는 모든 인증 요청에
`credentials: "include"`를 사용합니다.

## 생성 명세의 보안 표시

- 공개 API: 보안 요구 없음
- 보호된 GET: `sessionCookie`
- 보호된 POST·PATCH·DELETE: `sessionCookie`와 `csrfHeader`
- 모든 작업: 상세 설명과 파라미터 설명
- JSON 요청 작업: 실제 요청 예제
- 업무 규칙에 따라 404·409·422·429·503 오류와 예시 코드 표시

OpenAPI의 보안 표시는 클라이언트 문서화를 위한 계약이며 실제 접근 제어는 Spring
Security가 수행합니다.

## 운영 환경

환경 변수로 문서 노출을 끌 수 있습니다.

```bash
OPENAPI_ENABLED=false
```

이 값은 OpenAPI JSON/YAML, Scalar, Swagger UI를 함께 비활성화합니다. 내부 개발
서버에서는 기본값 `true`를 사용할 수 있지만 공개 운영 서버에서는 팀의 노출 정책을
정한 뒤 설정해야 합니다.

## 문서 관리 원칙

- 요청·응답 필드와 검증 조건: Java DTO와 생성 OpenAPI가 기준
- 인증 흐름·도메인 규칙·알려진 제한: `docs/backend/*.md`가 기준
- Controller를 추가하거나 경로를 변경하면 `@Operation`,
  `OpenApiDocumentationCatalog`와 관련 Markdown을 함께 수정
- CI 또는 로컬에서 `/v3/api-docs` 생성 테스트를 통과시켜 누락된 도메인이 없는지 확인
- 외부 전달 전 health `data.commit`과 배포 대상 `main` 커밋이 같은지 확인

현재 소셜 로그인은 구현되지 않았으므로 OpenAPI에도 OAuth authorization flow가 없습니다.
