# OpenAPI·API 문서 사용법

백엔드는 `springdoc-openapi`로 실행 중인 Controller와 DTO에서 OpenAPI 3 명세를
생성합니다. OpenAPI JSON/YAML이 프론트와 백엔드 사이의 기계 판독 가능한 API 계약이고,
Scalar는 그 계약을 사람이 읽고 요청을 시험하는 단일 문서 화면입니다.

`/v3/api-docs`의 `v3`는 OpenAPI 문서 규격 세대를 의미합니다. 서비스 API 버전은
`/api/v1`이며 OpenAPI `info.version`도 `v1`입니다.

## 프론트 연동에서의 역할

OpenAPI는 특정 언어나 화면 도구에 종속되지 않는 HTTP API 명세 표준입니다. 이
프로젝트에서는 다음 작업의 공통 입력으로 사용합니다.

- 프론트 개발자가 경로·메서드·파라미터·요청/응답 스키마·인증 요구사항 확인
- Scalar에서 예제 요청 실행
- TypeScript 타입과 API 클라이언트 코드 생성
- Mock 서버, 계약 테스트, API 변경 감지

OpenAPI가 요청·응답의 기술 계약을 담당하고, `FRONTEND.md`와 도메인 Markdown 문서는
로그인 순서, 화면 분기, 업무 규칙처럼 명세만으로 충분히 전달하기 어려운 흐름을
보완합니다.

## 팀 테스트 서버

| 용도 | URL |
|---|---|
| Cloud Run API·Scalar | `https://pl-timetable-api-532874992461.asia-northeast3.run.app/` |
| 기본 API 문서(Scalar) | `https://timetable-api.kdhoon.me/` |
| Scalar 직접 주소 | `https://timetable-api.kdhoon.me/scalar` |
| OpenAPI JSON | `https://timetable-api.kdhoon.me/v3/api-docs` |
| OpenAPI YAML | `https://timetable-api.kdhoon.me/v3/api-docs.yaml` |
| 서버 상태 | `https://timetable-api.kdhoon.me/api/v1/health/live` |

API 전용 호스트의 루트(`/`)는 Scalar로 이동합니다. Scalar는 `/v3/api-docs`를 읽어
화면을 만들므로 요청·응답 계약의 원본은 OpenAPI 명세 하나입니다.

Cloud Run 서비스와 기존 Cloudflare Tunnel 주소는 HTTPS로 제공되며 데이터베이스
포트는 공개하지 않습니다. 인증은 OTP 또는 Google OIDC 뒤 동일한 서버 세션을
사용합니다. Google 운영 로그인은 표준 웹 OAuth 클라이언트 생성 뒤 활성화합니다.

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
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| OpenAPI YAML | `http://localhost:8080/v3/api-docs.yaml` |

위 문서 경로는 인증 없이 조회할 수 있습니다.

## 인증된 API 시험

OTP 또는 Google 로그인으로 애플리케이션 세션을 만든 뒤 시험합니다.

1. `/api/v1/auth/otp/request`·`/api/v1/auth/otp/verify`를 호출하거나 브라우저를
   `/api/v1/auth/google`로 이동합니다.
2. 브라우저가 받은 `JSESSIONID` 쿠키를 유지합니다.
3. `GET /api/v1/auth/csrf`의 `data.token`을 받습니다.
4. GET 이외의 보호 API에는 토큰을 `X-XSRF-TOKEN` 헤더로 전송합니다.
5. Scalar의 인증 입력란에 `csrfHeader` 값을 입력합니다.

`sessionCookie`는 브라우저가 API 서버 쿠키를 자동으로 전송하므로 Scalar에 직접
입력할 필요가 없습니다. 프론트 애플리케이션에서는 모든 인증 요청에
`credentials: "include"`를 사용합니다.

## 생성 명세의 보안 표시

- 공개 API: 보안 요구 없음
- 보호된 GET: `sessionCookie`
- 보호된 POST·PATCH·DELETE: `sessionCookie`와 `csrfHeader`
- 모든 작업: 상세 설명과 파라미터 설명
- JSON 요청 작업: 실제 요청 예제와 요청 DTO 필드별 의미·예제·허용 값
- 공통 응답·페이지·사용자·시간표·자동편성 핵심 스키마: 필드별 의미 표시
- 업무 규칙에 따라 404·409·422·429·503 오류와 예시 코드 표시

Scalar에서 작업을 열고 **Body → Schema**를 보면 필드별 설명, 필수 여부, 형식과 예제를
확인할 수 있습니다. **Example**은 그대로 복사해 호출 가능한 요청 본문이며, enum은
Schema의 허용 값 중 하나를 사용합니다. 전체 응답에서는 `code`와 `message`가 아니라
`data` 안의 도메인 스키마를 확인합니다.

OpenAPI의 보안 표시는 클라이언트 문서화를 위한 계약이며 실제 접근 제어는 Spring
Security가 수행합니다.

## 운영 환경

환경 변수로 문서 노출을 끌 수 있습니다.

```bash
OPENAPI_ENABLED=false
```

이 값은 OpenAPI JSON/YAML과 Scalar를 함께 비활성화합니다. 내부 개발 서버에서는
기본값 `true`를 사용할 수 있지만 공개 운영 서버에서는 팀의 노출 정책을 정한 뒤
설정해야 합니다.

## 문서 관리 원칙

- 요청·응답 필드와 검증 조건: Java DTO와 생성 OpenAPI가 기준
- 인증 흐름·도메인 규칙·알려진 제한: `docs/backend/*.md`가 기준
- Controller를 추가하거나 경로를 변경하면 `@Operation`,
  `OpenApiDocumentationCatalog`와 관련 Markdown을 함께 수정
- 요청 DTO를 추가하거나 필드를 변경하면 `@Schema` 설명·예제도 함께 수정
- CI 또는 로컬에서 `/v3/api-docs` 생성 테스트를 통과시켜 누락된 도메인이 없는지 확인
- 외부 전달 전 health `data.commit`과 배포 대상 `main` 커밋이 같은지 확인

Google 로그인은 브라우저 리다이렉트 경로로 문서화한다. 보호된 업무 API는 외부
access token이 아니라 로그인 뒤 발급한 `sessionCookie` 보안 계약을 계속 사용한다.
