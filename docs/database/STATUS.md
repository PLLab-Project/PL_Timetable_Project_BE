# Database implementation status

이 문서는 현재 DB 기반에서 **실제로 구현된 범위**, **구조만 준비된 범위**, **구현되지 않은
범위**를 구분합니다.

## 구현 및 검증 완료

- PostgreSQL 18.4 Docker Compose 로컬 실행
- Flyway 4개 마이그레이션
- Spring Data JPA와 PostgreSQL JDBC 연결 설정
- Testcontainers에서 빈 PostgreSQL DB 마이그레이션 테스트
- 별도 보관된 로컬 페이로드로 현재 강의 카탈로그 2026-1 적재 검증
- 별도 보관된 로컬 페이로드로 2020~2026 과거 강의 개설 이력 적재 검증
- 별도 보관된 로컬 페이로드로 2016~2026 교육과정 필수과목 적재 검증
- 별도 보관된 로컬 페이로드로 2020~2026 졸업 학점·교양요건 적재 검증
- 별도 보관된 로컬 페이로드로 2026 학과별 졸업인증·구 졸업시험 자료 적재 검증
- 적재 페이로드 SHA-256 검증
- 35개 기준·적재관리 테이블 행 수와 핵심 참조 무결성 검증
- 동일 패키지 재실행 시 사용자 리뷰·이수과목을 변경하지 않는 적재 건너뛰기

## DB 구조만 준비됨

- `users`: 공급자와 독립된 서비스 사용자 UUID
- `social_identities`: 소셜 공급자 식별자 연결
- `student_profiles`: 사용자 계정과 분리된 학번·학과·입학연도
- `privacy_consents`: 개인정보 동의 이력
- `course_reviews`: 사용자 리뷰
- `completed_courses`: 사용자 이수과목

위 테이블은 API와 인증 코드가 아직 없으므로 테이블이 존재한다는 사실만으로 해당 기능이
동작하지는 않습니다.

## 구현되지 않음

- Spring MVC 기반 학사 조회·검색 API
- Spring Security
- OAuth2 Client와 Google·Kakao·Naver 등 소셜 공급자 설정
- 로그인 성공·실패 처리
- 로그인 세션 또는 토큰 발급·폐기
- 계정 연결·해제·탈퇴 정책
- 리뷰와 이수과목 CRUD API
- 졸업요건 판정 API
- 시간표·즐겨찾기·공유·추천 테이블과 API
- OCR

## 포함하지 않은 인증·사용자 데이터

- 이메일 OTP challenge
- OTP 재시도·rate event
- 로그인 session
- 사용자 계정 행과 개인정보
- 최적화 작업·저장 시간표·공유 링크

현재 계정 구조는 OTP 인증 스키마를 포함하지 않습니다. 소셜 로그인을 연결할 수 있도록
사용자와 학사 프로필을 분리한 최소 DB 계약이지만, 실제 소셜 로그인 기능은 Yuki 담당
구현과 리뷰가 필요합니다.

## 병합 전 필수 확인

- 빈 PostgreSQL 18.4 DB의 Flyway 마이그레이션과 기준 데이터 적재 검증
- `users`, `social_identities`, `privacy_consents`에 대한 Yuki 리뷰
- 향후 시간표 FK와 현재 강의·분반 복합키에 대한 준서 확인
- 공개 Git 저장소 밖에서 기준 데이터 페이로드를 전달·보관하는 방식에 대한 팀 합의

특히 identity 계약은 Yuki의 확인 전까지 팀의 최종 인증 스키마로 확정된 것으로
간주하지 않습니다.
