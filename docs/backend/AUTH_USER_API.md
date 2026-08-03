# 인증·사용자 API

인증 방식은 학교 이메일 OTP와 서버 주도 Google OIDC 로그인입니다. 두 방식 모두
로그인 뒤 JDBC에 저장되는 동일한 `JSESSIONID` 애플리케이션 세션을 사용합니다.

## 공통 응답

이 문서의 인증·사용자 API 성공 응답은 다음 envelope를 사용합니다.

```json
{
  "code": "SUCCESS",
  "message": "요청을 성공적으로 처리했습니다.",
  "data": {}
}
```

## 로그인

| Method | Path | 인증 | CSRF | 설명 |
|---|---|---:|---:|---|
| GET | `/api/v1/auth/csrf` | 불필요 | 불필요 | SPA 상태 변경 요청용 CSRF 토큰 발급 |
| POST | `/api/v1/auth/otp/request` | 불필요 | 제외 | 학교 이메일로 OTP 요청 |
| POST | `/api/v1/auth/otp/verify` | 불필요 | 제외 | OTP 검증 후 세션 생성 |
| GET | `/api/v1/auth/google` | 불필요 | 불필요 | Google 로그인 시작 |
| POST | `/api/v1/auth/school-verification/request` | 필요 | 필요 | 최초 Google 사용자의 학교 이메일 OTP 요청 |
| POST | `/api/v1/auth/school-verification/verify` | 필요 | 필요 | OTP 확인 후 Google 계정에 학번 연결 |
| GET | `/api/v1/auth/session` | 필요 | 불필요 | 현재 로그인 세션 조회 |
| POST | `/api/v1/auth/logout` | 필요 | 필요 | 세션 무효화 및 로그아웃 |

### CSRF 토큰

프론트와 API Origin이 달라도 쿠키를 직접 읽지 않도록 토큰을 응답 본문으로 제공합니다.

```json
{
  "code": "SUCCESS",
  "message": "요청을 성공적으로 처리했습니다.",
  "data": {
    "headerName": "X-XSRF-TOKEN",
    "parameterName": "_csrf",
    "token": "현재 브라우저 세션에 연결된 토큰"
  }
}
```

브라우저는 요청에 `credentials: "include"`를 사용하고 `data.token`을 메모리에
보관합니다. 로그인으로 세션 ID가 바뀐 뒤 다시 조회하며 POST·PATCH·DELETE 요청의
`X-XSRF-TOKEN` 헤더에 사용합니다.

### OTP 요청

```json
{
  "studentNumber": "20201234"
}
```

- `studentNumber`: 숫자 6~20자리
- 학교 이메일 주소는 서버 설정의 도메인과 학번으로 구성

응답의 `data`:

```json
{
  "message": "인증번호를 전송했습니다.",
  "cooldownSeconds": 60,
  "expiresInSeconds": 300
}
```

### OTP 검증

```json
{
  "studentNumber": "20201234",
  "code": "123456"
}
```

성공하면 `JSESSIONID` 세션 쿠키가 설정됩니다.

```json
{
  "code": "SUCCESS",
  "message": "요청을 성공적으로 처리했습니다.",
  "data": {
    "authenticated": true,
    "user": {
      "id": "3c8fb145-a10f-4df8-818a-a213ef8b3fc5",
      "studentNumber": "20201234",
      "name": null
    },
    "newUser": true,
    "expiresAt": "2026-07-24T05:00:00Z"
  }
}
```

### Google 로그인

운영에서는 브라우저를 Vercel 동일 출처 경로
`GET /oauth2/authorization/google`로 이동시킨다. Vercel이 로그인 시작과
`/login/oauth2/code/google` 콜백을 Cloud Run으로 프록시하고, 백엔드가 로컬 계정과
연결해 JDBC 세션을 만든다. 외부 access/refresh token은 저장하지 않으며 불변 Google
`sub`만 `social_identities`에 저장한다.

Google OAuth 웹 클라이언트의 승인된 리디렉션 URI와 Cloud Run의 Spring 등록
리디렉션 URI는 모두 다음 값으로 일치시킨다.

```text
https://pl-timetable-project-fe.vercel.app/login/oauth2/code/google
```

프론트에서는 Google 로그인 API를 `fetch`로 호출하지 않고 브라우저 전체를
이동시켜야 한다.

```javascript
window.location.assign("/oauth2/authorization/google");
```

로컬 개발 또는 동일 출처 프록시를 사용하지 않는 환경만
`${API_BASE_URL}/api/v1/auth/google` 직접 이동을 사용한다.

인증 성공 시 기본적으로 다음 주소로 돌아온다.

```text
https://pl-timetable-project-fe.vercel.app/?auth=google-success
```

실패 시에는 `auth=google-failure`로 돌아온다. 프론트는 성공 리다이렉트를 받은 뒤
`credentials: "include"`로 세션을 조회한다.

```javascript
const response = await fetch(
  `${API_BASE_URL}/api/v1/auth/session`,
  { credentials: "include" }
);
const session = (await response.json()).data;
```

세션이 유효하면 `/api/v1/users/me`로 최신 프로필을 조회한다.
사용자 프로필의 `profileCompleted`가 `false`이면 회원정보 입력 화면으로 이동하고,
이미 작성된 사용자라면 앱 화면으로 이동한다. `schoolVerified`는 선택적인 학교
인증 상태이며 학생 기능 접근을 제한하지 않는다.
성공·실패 리다이렉트는 배포 환경의
`GOOGLE_OAUTH_SUCCESS_REDIRECT_URI`, `GOOGLE_OAUTH_FAILURE_REDIRECT_URI`로
바꿀 수 있다.

### 선택적 학교 OTP 인증

Google 로그인이 기본 인증 수단이며 학교 이메일 OTP는 필수가 아니다. 유효한 Google
세션은 `schoolVerified=false`여도 자동편성·시간표 등 학생 기능을 사용할 수 있다.
학교 학번 소유 확인이 별도로 필요한 운영 정책을 도입할 때만 아래 API를 선택적으로
사용한다.

1. `GET /api/v1/auth/csrf`로 CSRF 토큰을 받는다.
2. 아래 요청으로 `학번@daejin.ac.kr`에 OTP를 보낸다.

```http
POST /api/v1/auth/school-verification/request
Content-Type: application/json
X-XSRF-TOKEN: {csrfToken}

{
  "studentNumber": "20261234"
}
```

3. 이메일로 받은 6자리 번호를 현재 Google 세션에서 확인한다.

```http
POST /api/v1/auth/school-verification/verify
Content-Type: application/json
X-XSRF-TOKEN: {csrfToken}

{
  "studentNumber": "20261234",
  "code": "123456"
}
```

성공하면 학번과 `schoolVerifiedAt`을 저장하고 세션 ID를 다시 발급한다. 이후
Google 로그인에서는 OTP를 반복하지 않으며, 학번을 변경할 때만 새 학번의 학교
이메일 OTP를 다시 확인한다.

## 사용자

모든 사용자 API는 로그인 세션이 필요하고, GET이 아닌 요청은 CSRF 헤더가 필요합니다.

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/users/me` | 내 회원·학생 프로필 조회 |
| PATCH | `/api/v1/users/me` | 내 프로필의 전달된 필드만 수정 |
| POST | `/api/v1/users/me/privacy-consents` | 개인정보 동의 내역 저장 |
| GET | `/api/v1/users/me/privacy-consents` | 개인정보 동의 이력 조회 |
| DELETE | `/api/v1/users/me` | 계정과 연결된 사용자 데이터 삭제 |

### 프로필 수정

모든 필드는 선택 사항이고 `null`인 필드는 기존 값을 유지합니다.

```json
{
  "name": "홍길동",
  "grade": 3,
  "departmentId": "CSE",
  "admissionYear": 2022,
  "studentType": "DOMESTIC",
  "academicPrograms": [
    { "academicUnitCode": "CSE", "role": "PRIMARY" },
    { "academicUnitCode": "BUS", "role": "DOUBLE_MAJOR" },
    { "academicUnitCode": "JPN", "role": "MINOR" }
  ],
  "tutorialCompleted": true
}
```

- `name`: 최대 120자
- `grade`: 1~6
- `departmentId`: 최대 40자
- `studentNumber`: Google 로그인 후 온보딩 또는 내 정보에서 직접 설정·변경
- `admissionYear`: 1900~2100
- `programPath`: `ADVANCED_MAJOR`, `DOUBLE_MAJOR`, `MINOR`, `MICRO_MAJOR`
- `academicPrograms`: 주전공을 포함한 전체 전공 목록. 전달하면 기존 목록을 교체하며
  `PRIMARY`는 정확히 하나여야 한다. 같은 학과를 여러 역할로 중복 등록할 수 없다.
- 새 클라이언트는 `academicPrograms`를 사용한다. `programPath`는 기존 클라이언트를 위한
  요약값이며, 목록을 전달하면 서버가 `DOUBLE_MAJOR` → `MINOR` → `MICRO_MAJOR` →
  `ADVANCED_MAJOR` 우선순위로 계산한다.

사용자 응답의 `data`:

```json
{
  "id": "3c8fb145-a10f-4df8-818a-a213ef8b3fc5",
  "studentNumber": "20201234",
  "name": "홍길동",
  "grade": 3,
  "departmentId": "CSE",
  "department": "컴퓨터공학과",
  "admissionYear": 2022,
  "studentType": "DOMESTIC",
  "programPath": "ADVANCED_MAJOR",
  "profileCompleted": true,
  "graduationProfileCompleted": true,
  "tutorialCompleted": true,
  "schoolVerified": true,
  "schoolVerifiedAt": "2026-07-31T06:00:00Z",
  "createdAt": "2026-07-24T04:00:00Z",
  "academicPrograms": [
    {
      "id": "50db78a7-cf6e-445f-b99c-e4211d8669d6",
      "academicUnitCode": "CSE",
      "academicUnitName": "컴퓨터공학과",
      "role": "PRIMARY",
      "status": "ACTIVE",
      "displayOrder": 0
    }
  ]
}
```

`studentType`을 보내지 않은 국내 학생 온보딩은 현재 지원 졸업요건 데이터에 맞춰
`DOMESTIC`으로 저장한다. 전공 목록의 개수는 컬럼으로 제한하지 않으며, 학교 정책상
허용 개수는 애플리케이션 정책으로 검증할 수 있도록 관계 행으로 저장한다.

### 개인정보 동의

```json
{
  "consentVersion": "privacy-v1",
  "agreed": true
}
```

응답 항목은 `consentId`, `consentVersion`, `agreed`, `agreedAt`입니다. GET은 저장된
동의 이력을 배열로 반환합니다.

### 회원 탈퇴

실수로 호출되는 것을 방지하기 위해 확인 값이 필수입니다.

```json
{
  "confirmed": true
}
```

성공하면 사용자 계정과 연결된 시간표, 자동 편성 작업, 이수과목, 리뷰, 개인정보 동의,
OTP challenge가 삭제되고 현재 세션도 무효화됩니다.

```json
{
  "code": "SUCCESS",
  "message": "요청을 성공적으로 처리했습니다.",
  "data": {
    "message": "회원 탈퇴가 완료되었습니다.",
    "deletedAt": "2026-07-24T05:10:00Z"
  }
}
```

## 브라우저 호출 규칙

```javascript
const csrfResponse = await fetch(
  `${API_BASE_URL}/api/v1/auth/csrf`,
  { credentials: "include" }
);
const csrf = (await csrfResponse.json()).data.token;

await fetch(`${API_BASE_URL}/api/v1/users/me`, {
  method: "PATCH",
  credentials: "include",
  headers: {
    "Content-Type": "application/json",
    "X-XSRF-TOKEN": csrf
  },
  body: JSON.stringify({ name: "홍길동", grade: 3, departmentId: "CSE" })
});
```

재사용 가능한 TypeScript 래퍼는
[프론트엔드 연결 가이드](../../FRONTEND.md)를 확인합니다.
