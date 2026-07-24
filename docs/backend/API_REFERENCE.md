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
| 프론트 연동 공통사항 | [FRONTEND_API_HANDOFF.md](FRONTEND_API_HANDOFF.md) |
| 실제 HTTP 스모크 테스트 | [LOCAL_API_SMOKE_TEST.md](LOCAL_API_SMOKE_TEST.md) |

## 공통 인증 규칙

- 세션 쿠키: `JSESSIONID`
- 브라우저 요청: `credentials: "include"` 사용
- 인증된 상태 변경 요청: `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더에 포함
- OTP 요청·검증, 공개 학사 조회, Scalar·Swagger·OpenAPI 명세는 인증 없이 접근 가능
- 다른 사용자의 소유 데이터에는 접근할 수 없음

## 현재 계약상 주의점

성공 응답 형식이 아직 하나로 통일되지 않았습니다.

- 인증·사용자·일부 이수과목 응답: `ApiResponse<T>` envelope
- 학사·리뷰·졸업요건·시간표·자동 편성 응답: DTO를 본문에 직접 반환
- 예외 응답도 두 공통 처리 형식이 공존

프론트에서는 당분간 엔드포인트별 스키마를 OpenAPI에서 확인해야 합니다. 백엔드가 공통
응답 형식을 변경하면 API 계약 변경으로 취급하고 프론트와 함께 반영해야 합니다.

## 아직 구현되지 않은 주요 기능

- 소셜 로그인
- 졸업판정용 `admissionYear`, `studentType`, `programPath` 사용자 프로필 입력
- 시간표 복사, 공유 링크, 이전 학기 조회, 대체 분반 추천
- OCR

이 목록은 현재 구현 범위를 구분하기 위한 것이며, 구현된 API의 동작 여부와는 별개입니다.
