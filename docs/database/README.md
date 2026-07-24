# Database foundation

## 적용된 구성

- PostgreSQL `18.4`
- Flyway `12.11.0`과 버전 마이그레이션 8개
- Spring Data JPA와 PostgreSQL JDBC 드라이버
- Testcontainers PostgreSQL 통합 테스트
- Docker Compose의 `db`, `migrate`, `ingest`, `api` 서비스

Flyway는 테이블·제약조건·인덱스 같은 **DB 구조**를 관리합니다. 약 2만 건의 과거 분반과
원천 payload를 포함하는 기준 데이터는 `data/database`의 압축 SQL 적재 페이로드로
분리되어 있습니다. 이 페이로드는 공개 Git 저장소에서 배포하지 않고 승인된 별도 경로를
통해 로컬로 제공하며, 애플리케이션 기동 시 자동으로 대용량 데이터를 넣지는 않습니다.

전체 물리 스키마는 [`ERD.html`](ERD.html)에서 검색·도메인 필터·관계 강조 기능과 함께
확인할 수 있습니다. 간략한 도메인 관계 설명은 [`ERD.md`](ERD.md)를 참고합니다.

## 실행 순서

별도로 전달받은 `academic-data-bundle.tar.gz` 한 파일을 `data/database/`에 배치한
뒤 저장소 루트에서 실행합니다. 번들은 `.gitignore` 대상이며 실행 스크립트가 내부
페이로드를 자동으로 풀고 검증합니다.

```bash
./start.sh
```

`ingest`는 다음 순서로 동작합니다.

1. 번들에서 풀린 현재 카탈로그와 내부 기준 데이터 조각의 SHA-256 검증
2. `reference_data_imports.package_id`로 기준 데이터 패키지의 기존 적재 여부 확인
3. `data_imports.checksum`으로 현재 학기 카탈로그의 기존 적재 여부 확인
4. 미적재 패키지만 트랜잭션으로 적재
5. 학과·전공 코드, 연도별 별칭, 현재 분반 연결 정규화
6. `expected-row-counts.tsv`에 정의된 39개 기준·적재관리 테이블의 행 수 확인
7. 핵심 참조 무결성과 Flyway 실패 이력 확인

동일 패키지를 다시 실행하면 적재 단계는 건너뛰고 검증만 수행합니다. 따라서 같은
패키지의 재실행은 `users`, `course_reviews`, `completed_courses`를 변경하지 않습니다.
학과 정규화는 매번 같은 원천에서 다시 계산하며 중복 행을 만들지 않습니다.

## 학과·전공 정규화

- `academic_colleges`: 단과대 코드와 최신 명칭
- `academic_units`: 공식 학과·전공 코드와 공식 코드가 없는 과거 요건용 결정적 파생 코드
- `academic_unit_aliases`: 교육과정·졸업요건에서 사용된 연도별 표기
- `section_academic_units`: 현재 분반과 명시적으로 제공된 학과 코드의 다대다 관계
- `student_profiles.academic_unit_code`: 사용자 학과를 `academic_units`에 연결하는 FK

`academic_units.is_current`는 최신 교육과정 데이터셋에 코드가 존재한다는 의미이며,
신입생 모집 또는 행정조직 운영 상태를 보장하지 않습니다. 학과 코드가 없는 교양·공통
강의는 `section_academic_units`에 임의 학과를 배정하지 않습니다.

공식 학과 코드가 없는 과거 교육과정·졸업요건의 학과 키는 `REQ-` 접두사의 결정적 코드로
분리합니다. 이 코드는 현재 학과라고 추정하거나 기존 공식 코드에 임의 통합하지 않으면서도
모든 요건 행이 `academic_units`를 FK로 참조할 수 있게 합니다. 학과 목록 API의 기본값은
`code_source = 'OFFICIAL_CURRICULUM' AND is_current = true` 범위가 적절합니다.

## 실제 DB 저장 위치

Compose는 bind mount가 아니라 `pl-timetable-team-db_postgres_data`라는 Docker named
volume을 사용합니다.

- 컨테이너 마운트: `/var/lib/postgresql`
- PostgreSQL 18 데이터 디렉터리: `/var/lib/postgresql/18/docker`
- 호스트의 실제 경로는 Docker가 관리하므로 Git에 포함되지 않음

`docker compose down`은 볼륨을 보존하고, `docker compose down -v`는 해당 로컬 DB
볼륨을 삭제합니다.

## 애플리케이션 연결

| 환경 변수 | 로컬 기본값 |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:15432/pl_timetable` |
| `DATABASE_USERNAME` | `pl_timetable` |
| `DATABASE_PASSWORD` | `local-only-change-me` |

기본 비밀번호는 로컬 개발 전용입니다. 배포 환경에서는 환경변수 또는 비밀 관리 도구로
별도 값을 주입해야 합니다. Hibernate는 `ddl-auto=validate`로 설정되어 DDL을 생성하거나
기존 스키마를 수정하지 않습니다.

API 컨테이너는 내부 Docker 네트워크에서 `db:5432`에 연결하므로 호스트 Java나
PostgreSQL 설치가 필요하지 않습니다. 학교 서버 운영 절차는
[`../deployment/SCHOOL_SERVER.md`](../deployment/SCHOOL_SERVER.md), DB 백업·복구는
[`../deployment/BACKUP_RESTORE.md`](../deployment/BACKUP_RESTORE.md)를 따릅니다.

## 시간표·자동 편성 저장 구조

- `timetables`: 사용자 UUID와 정식 `semester_id`를 참조하는 시간표
- `timetable_courses`: 시간표가 선택한 분반 복합키와 표시용 스냅샷
- `timetable_course_meetings`: 선택 분반의 요일과 자정 기준 시작·종료 분 스냅샷
- `optimization_jobs`: 사용자·시간표·학기와 연결된 자동 편성 작업 상태
- `optimization_job_excluded_days`: 제외 요일 조건
- `optimization_job_required_sections`: 반드시 포함할 정확한 분반 복합키
- `optimization_results`: 작업별 순위 결과와 점수
- `optimization_result_course_slots`: 결과에 포함된 분반별 수업시간 스냅샷

시간표와 자동 편성 API는 클라이언트가 보낸 과목명·교수·학점·시간을 저장하지 않습니다.
`semester_id`, `course_code`, `section_code`로 `courses`, `sections`, `sessions`를 조회한 뒤
검증된 학사 원본만 스냅샷으로 보관합니다. 사용자 FK는 `users.id`의 UUID를 사용합니다.
수업시간은 PostgreSQL/JVM 시간대 설정에 영향을 받지 않도록 기존 `sessions`와 같은
자정 기준 분(`smallint`, 0~1440)으로 저장하고 API 경계에서 `LocalTime`으로 변환합니다.

## 인증 관련 범위

현재 마이그레이션에는 `users`, `student_profiles`, `privacy_consents`,
`social_identities`, `login_otp_challenges`가 포함됩니다. 학교 이메일 OTP와 Spring
Security 서버 세션은 구현되어 있습니다. `social_identities`는 향후 공급자 식별자
연결을 위한 계약이며 OAuth2 Client·소셜 공급자 콜백은 아직 구현되지 않았습니다.
