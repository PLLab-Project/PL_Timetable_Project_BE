# 프론트엔드 연결 가이드

이 문서는 현재 구현된 백엔드의 프론트 연동 경계를 요약합니다. 요청·응답 필드와 검증
조건은 실행 서버의 OpenAPI가 기준이고, 도메인 Markdown은 업무 규칙과 제한을 설명합니다.

## 연결 기본값

- API prefix: `/api/v1`
- 팀 테스트 서버: `https://timetable-api.kdhoon.me`
- 기본 API 문서(Scalar): `https://timetable-api.kdhoon.me/`
- Swagger UI: `https://timetable-api.kdhoon.me/swagger-ui.html`
- 로컬 Docker API: `http://127.0.0.1:18082`
- 소스 직접 실행 API: `http://localhost:8080`
- 기본 허용 프론트 Origin: `http://localhost:5173`
- 세션 인증: 브라우저 요청에 쿠키 포함 (`credentials: "include"`)
- CSRF 초기화: `GET /api/v1/auth/csrf` 응답의 `data.token`을 메모리에 저장
- 상태 변경 요청: CSRF 토큰을 `X-XSRF-TOKEN` 헤더로 전송
- OTP 요청·검증 경로는 CSRF 검사에서 제외

프론트 저장소에서는 API 주소를 직접 작성하지 않고 환경변수를 사용합니다.

```env
VITE_API_BASE_URL=https://timetable-api.kdhoon.me
```

로컬 프론트에서 팀 테스트 서버를 호출하려면 백엔드의 `ALLOWED_ORIGINS`에
`http://localhost:5173`이 포함되어 있어야 합니다. 세션 쿠키를 사용하는 CORS에서는
`*`를 허용 Origin으로 사용할 수 없습니다.

## 인증 경계

인증 없이 가능한 API:

- `GET /auth/csrf`
- `POST /auth/otp/request`
- `POST /auth/otp/verify`
- `GET /health/**`
- `GET /departments/**`
- `GET /semesters/**`
- `GET /courses/**`
- `GET /graduation/rules`

인증이 필요한 영역:

- `/auth/session`, `/auth/logout`
- `/users/me/**`
- `/reviews/**`의 작성·내 목록·수정·삭제
- `/completed-courses/**`
- `/graduation/evaluation`, `/graduation/me/evaluation`
- `/timetables/**`
- `/optimizations/**`

인증이 없거나 세션이 만료되면 HTTP `401`과 `AUTH_SESSION_EXPIRED`가 반환됩니다.

## 도메인별 명세

- [전체 API 문서 인덱스](docs/backend/API_REFERENCE.md)
- [인증·사용자](docs/backend/AUTH_USER_API.md)
- [학과·학기·강의·리뷰](docs/backend/ACADEMIC_API.md)
- [이수과목](docs/backend/COMPLETED_COURSE_API.md)
- [졸업요건](docs/backend/GRADUATION_API.md)
- [시간표](docs/backend/TIMETABLE_API.md)
- [자동 편성](docs/backend/OPTIMIZATION_API.md)
- [OpenAPI·API 문서 사용법](docs/backend/OPENAPI.md)
- [로컬 실사용 스모크 테스트 결과](docs/backend/LOCAL_API_SMOKE_TEST.md)

실행 중인 서버의 루트(`/`)와 `/scalar`에서 대화형 문서를 확인할 수 있습니다.
`/swagger-ui.html`은 Swagger UI를 제공하고, `/v3/api-docs`와
`/v3/api-docs.yaml`은 기계 판독 가능한 OpenAPI 3.1 명세입니다. 여기서 `v3`는
문서 규격 세대이고 서비스 API 버전은 `/api/v1`입니다.

## 공통 응답 형식

모든 API 성공·실패 응답은 다음 envelope를 사용합니다.

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
- 오류도 같은 세 필드를 사용하며 `data=null`입니다.
- 화면 분기는 메시지 문자열이 아니라 HTTP 상태와 안정적인 `code`를 사용합니다.

## 권장 fetch 래퍼

프론트와 API Origin이 다르면 API가 설정한 쿠키를 프론트 JavaScript가 직접 읽을 수
없습니다. 공개 CSRF 초기화 API에서 토큰을 받아 메모리에 보관합니다.

```typescript
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

type ApiEnvelope<T> = {
  code: string;
  message: string;
  data: T;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string
  ) {
    super(message);
  }
}

let csrfToken: string | null = null;

export async function refreshCsrf(): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/csrf`, {
    credentials: "include"
  });
  const body = (await response.json()) as ApiEnvelope<{ token: string }>;

  if (!response.ok) {
    throw new ApiError(response.status, body.code, body.message);
  }
  csrfToken = body.data.token;
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {}
): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  if (MUTATING_METHODS.has(method) && csrfToken === null) {
    await refreshCsrf();
  }

  const headers = new Headers(init.headers);
  if (MUTATING_METHODS.has(method) && csrfToken !== null) {
    headers.set("X-XSRF-TOKEN", csrfToken);
  }
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    method,
    headers,
    credentials: "include"
  });
  const body = (await response.json()) as ApiEnvelope<T>;

  if (!response.ok) {
    if (response.status === 401) {
      csrfToken = null;
    }
    throw new ApiError(response.status, body.code, body.message);
  }
  return body.data;
}
```

OTP 로그인 성공으로 세션 ID가 바뀐 뒤에는 `refreshCsrf()`를 다시 호출합니다.
로그아웃과 회원 탈퇴 뒤에는 프론트 메모리의 토큰도 삭제합니다.

## 쿠키·CORS 배치

- 프론트와 API가 같은 사이트의 서브도메인이면 `SameSite=Lax`를 권장합니다.
- 서로 다른 최상위 사이트면 HTTPS와 `SameSite=None; Secure`가 필요합니다.
- 실제 프론트 Origin은 `ALLOWED_ORIGINS`에 정확히 등록해야 합니다.
- 세션 쿠키는 `HttpOnly`이므로 JavaScript에서 읽는 것이 정상 동작이 아닙니다.
- 개발자 도구의 Network 탭에서 요청 쿠키와 CORS 응답 헤더를 확인합니다.

## 오류 분기

- `400`: 입력·조회 조건 오류
- `401 AUTH_SESSION_EXPIRED`: 로그인 화면으로 이동
- `403 COMMON_FORBIDDEN`: 권한 또는 CSRF 오류
- `404`: 존재하지 않거나 소유하지 않은 리소스
- `409`: 시간표 충돌 또는 상태 전환 충돌
- `422`: 자동편성 가능 해 없음
- `429`: OTP 재요청·검증 횟수 제한
- `500`: 공통 오류 화면과 추적 로그

각 API에 가능한 업무 오류와 실제 요청 예제가 Scalar·Swagger에 표시됩니다.

## 백엔드 인수 완료 조건

1. 소셜 로그인 여부와 졸업판정용 학생 프로필 입력 흐름 확정
2. 프론트 개발·운영 Origin을 `ALLOWED_ORIGINS`에 등록
3. health 응답의 `data.commit`과 전달받은 `main` 커밋 일치 확인
4. 운영 배포 환경에서 OTP·세션·CSRF와 PostgreSQL 통합·인수 테스트 통과

이후 화면 상태 관리와 API 호출 연결은 프론트 팀이 담당하고, 백엔드 팀은 연동 과정에서
발견되는 계약 불일치와 서버 결함을 수정합니다.
