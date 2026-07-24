# 인증·사용자 API

현재 인증 방식은 학교 이메일 OTP와 서버 세션입니다. **소셜 로그인은 아직 구현되지
않았습니다.**

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
  "departmentId": "CSE"
}
```

- `name`: 최대 120자
- `grade`: 1~6
- `departmentId`: 최대 40자

사용자 응답의 `data`:

```json
{
  "id": "3c8fb145-a10f-4df8-818a-a213ef8b3fc5",
  "studentNumber": "20201234",
  "name": "홍길동",
  "grade": 3,
  "departmentId": "CSE",
  "department": "컴퓨터공학과",
  "createdAt": "2026-07-24T04:00:00Z"
}
```

현재 사용자 API에는 졸업판정 입력인 `admissionYear`, `studentType`, `programPath`를
저장하는 필드가 없습니다. 해당 값이 `student_profiles`에 없는 사용자는 졸업판정 API에서
`INVALID_ACADEMIC_QUERY`를 받습니다. 프론트 온보딩에서 판정까지 연결하려면 이 입력
계약을 별도로 추가해야 합니다.

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
[프론트엔드 API 연동 안내](FRONTEND_API_HANDOFF.md)를 확인합니다.
