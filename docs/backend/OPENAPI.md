# OpenAPI·Swagger 사용법

백엔드는 `springdoc-openapi`로 실행 중인 Controller와 DTO에서 OpenAPI 3 명세를
생성합니다.

## 로컬 주소

서버 기본 포트가 `8080`일 때:

| 용도 | URL |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| OpenAPI YAML | `http://localhost:8080/v3/api-docs.yaml` |

세 경로는 인증 없이 조회할 수 있습니다.

## 인증된 API 시험

현재 인증은 OAuth가 아니라 OTP 세션 방식입니다.

1. `/api/v1/auth/otp/request`와 `/api/v1/auth/otp/verify`를 호출합니다.
2. 브라우저가 받은 `JSESSIONID` 쿠키를 유지합니다.
3. GET 이외의 보호 API에는 `XSRF-TOKEN` 쿠키 값도 필요합니다.
4. Swagger UI의 **Authorize**에서 `csrfHeader`에 해당 값을 입력합니다.

`sessionCookie`는 브라우저가 동일 서버 쿠키를 자동으로 전송하므로 보통 Swagger UI에
직접 입력할 필요가 없습니다. 프론트 애플리케이션에서는 모든 인증 요청에
`credentials: "include"`를 사용합니다.

## 생성 명세의 보안 표시

- 공개 API: 보안 요구 없음
- 보호된 GET: `sessionCookie`
- 보호된 POST·PATCH·DELETE: `sessionCookie`와 `csrfHeader`

OpenAPI의 보안 표시는 클라이언트 문서화를 위한 계약이며 실제 접근 제어는 Spring
Security가 수행합니다.

## 운영 환경

환경 변수로 문서 노출을 끌 수 있습니다.

```bash
OPENAPI_ENABLED=false
```

이 값은 OpenAPI JSON/YAML과 Swagger UI를 함께 비활성화합니다. 내부 개발 서버에서는
기본값 `true`를 사용할 수 있지만 공개 운영 서버에서는 팀의 노출 정책을 정한 뒤
설정해야 합니다.

## 문서 관리 원칙

- 요청·응답 필드와 검증 조건: Java DTO와 생성 OpenAPI가 기준
- 인증 흐름·도메인 규칙·알려진 제한: `docs/backend/*.md`가 기준
- Controller를 추가하거나 경로를 변경하면 `@Operation` 설명과 관련 Markdown을 함께 수정
- CI 또는 로컬에서 `/v3/api-docs` 생성 테스트를 통과시켜 누락된 도메인이 없는지 확인

현재 소셜 로그인은 구현되지 않았으므로 OpenAPI에도 OAuth authorization flow가 없습니다.
