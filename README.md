# PL Timetable Project Backend

Spring Boot 기반 팀 시간표 프로젝트의 백엔드 저장소입니다. 현재 DB 기반에는
PostgreSQL 18.4 로컬 실행 환경, Flyway 스키마 마이그레이션, 정규화된 학사 기준 데이터를
적재·검증하는 스크립트가 포함되어 있습니다. 학사 데이터 SQL 페이로드는 공개 Git
저장소에 포함하지 않으며 승인된 별도 경로에서 로컬로 준비해야 합니다.

학과·전공은 `academic_units`의 안정적인 학과 코드를 기준으로 관리하고, 연도별 명칭과
졸업요건 표기는 `academic_unit_aliases`, 현재 분반 연결은 `section_academic_units`에서
관리합니다.

현재 main 기준으로 이메일 OTP 세션 인증과 Spring Security, 학사 조회·리뷰·이수과목·
졸업요건, 시간표·자동 편성 API가 구현되어 있습니다. 소셜 로그인과 일부 시간표 확장
기능은 아직 구현되지 않았습니다. 자세한 상태는
[구현 상태](docs/database/STATUS.md)를 확인합니다.

## 로컬 DB 생성

먼저 별도로 전달받은 다음 파일을 `data/database/`에 배치합니다.

- `current-catalog.sql.gz`
- `reference-data.sql.gz.part-00`
- `reference-data.sql.gz.part-01`
- `reference-data.sql.gz.part-02`

해당 파일은 `.gitignore` 대상이므로 로컬에 배치해도 Git에 추가되지 않습니다. 이후 저장소
루트에서 실행합니다.

```bash
cp .env.example .env
docker compose up -d --wait db
docker compose run --rm migrate
docker compose run --rm --no-deps ingest
```

- PostgreSQL 접속 주소: `127.0.0.1:15432`
- 기본 DB 이름: `pl_timetable`
- 애플리케이션 JDBC 기본 주소: `jdbc:postgresql://localhost:15432/pl_timetable`

동일한 기준 데이터 패키지를 다시 적재하면 체크섬과 적재 이력을 검증한 뒤 적재를
건너뜁니다. 로컬 DB를 완전히 삭제하고 빈 DB부터 재생성하려면 다음을 실행합니다.

```bash
docker compose down -v
```

이 명령은 `pl-timetable-team-db_postgres_data` Docker 볼륨의 로컬 데이터를 삭제합니다.

## 문서

- [외부 Swagger UI](https://timetable-api.kdhoon.me/swagger-ui.html)
- [백엔드 구조 원칙](docs/backend/ARCHITECTURE.md)
- [전체 백엔드 API 명세](docs/backend/API_REFERENCE.md)
- [프론트엔드 API 연동 안내](docs/backend/FRONTEND_API_HANDOFF.md)
- [OpenAPI·Swagger 사용법](docs/backend/OPENAPI.md)
- [인증·사용자 API](docs/backend/AUTH_USER_API.md)
- [학사 조회 API](docs/backend/ACADEMIC_API.md)
- [이수과목 API](docs/backend/COMPLETED_COURSE_API.md)
- [졸업요건 API](docs/backend/GRADUATION_API.md)
- [시간표 API](docs/backend/TIMETABLE_API.md)
- [자동 편성 API](docs/backend/OPTIMIZATION_API.md)
- [구현 완료·미구현 상태](docs/database/STATUS.md)
- [DB 구조와 실행 방법](docs/database/README.md)
- [전체 ERD 시각화](docs/database/ERD.html)
- [핵심 관계 개요](docs/database/ERD.md)
- [DB 규칙과 변경 절차](docs/database/CONVENTIONS.md)
- [데이터 범위·출처·완전성](docs/database/DATASET.md)
- [도메인 소유권](docs/database/OWNERSHIP.md)
