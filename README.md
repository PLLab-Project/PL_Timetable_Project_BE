# PL Timetable Project Backend

대진대학교 시간표·학사·졸업요건 팀 프로젝트의 Spring Boot 백엔드입니다.
PostgreSQL 18.4, Flyway, 정규화된 학사 기준 데이터, 세션 인증, 시간표와 OR-Tools
자동편성 API를 포함합니다.

현재 `main`에는 학교 이메일 OTP 세션 인증, 학과·학기·강의·리뷰, 이수과목·졸업요건,
시간표·자동편성 API가 구현되어 있습니다. 소셜 로그인, 일부 시간표 확장 기능과 OCR은
아직 구현되지 않았습니다. 정확한 범위는 [구현 상태](docs/database/STATUS.md)를
확인합니다.

## 가장 빠른 전체 실행

호스트에는 **Docker Engine, Docker Compose v2와 Git만 필요**합니다.
Java, Gradle, PostgreSQL은 컨테이너에 포함되거나 Gradle Wrapper 빌드 단계에서
자동으로 준비됩니다.

1. 별도로 전달받은 다음 파일을 `data/database/`에 배치합니다.

   - `current-catalog.sql.gz`
   - `reference-data.sql.gz.part-00`
   - `reference-data.sql.gz.part-01`
   - `reference-data.sql.gz.part-02`

   이 파일은 교수명과 원천 payload를 포함할 수 있어 공개 Git 저장소에 포함하지
   않습니다. 체크섬과 기대 행 수 파일은 저장소에 포함되어 있습니다.

2. 로컬 개발 설정을 준비하고 전체 스택을 시작합니다.

   ```bash
   cp .env.example .env
   ./scripts/bootstrap-school.sh
   ```

3. 기본 주소를 확인합니다.

   - API: `http://127.0.0.1:18082`
   - Scalar API 문서: `http://127.0.0.1:18082/`
   - Swagger UI: `http://127.0.0.1:18082/swagger-ui.html`
   - OpenAPI JSON: `http://127.0.0.1:18082/v3/api-docs`
   - 상태·배포 커밋: `http://127.0.0.1:18082/api/v1/health/live`
   - PostgreSQL: `127.0.0.1:15432`

`bootstrap-school.sh`는 DB 시작, 전체 Flyway 마이그레이션 적용, 학사 데이터 체크섬 검증·멱등 적재,
Spring Boot 이미지 빌드, API healthcheck까지 수행합니다. 동일한 데이터로 다시 실행해도
기준 데이터나 사용자 데이터를 중복 생성하지 않습니다.

## 학교 컴퓨터 배포

학교 서버에서는 운영 템플릿을 사용합니다.

```bash
cp .env.school.example .env
# .env의 DB 비밀번호, 프론트 주소, SMTP 설정을 실제 값으로 수정
./scripts/bootstrap-school.sh
```

기본적으로 API와 DB 포트는 호스트 루프백에만 바인딩됩니다. 외부 공개는 HTTPS
리버스 프록시나 Cloudflare Tunnel을 통해 API 포트만 연결하고 DB 포트는 공개하지
않습니다. 설치, 업데이트, CORS·쿠키, 외부 공개와 장애 확인 절차는
[학교 서버 배포 런북](docs/deployment/SCHOOL_SERVER.md)을 따릅니다.

## 프론트엔드 연결

프론트는 API 주소를 코드에 고정하지 않고 환경변수로 관리합니다.

```env
VITE_API_BASE_URL=https://timetable-api.kdhoon.me
```

세션 인증 요청은 `credentials: "include"`가 필요합니다. POST·PATCH·DELETE 요청은
`GET /api/v1/auth/csrf`가 반환한 `data.token`을 `X-XSRF-TOKEN` 헤더에 포함해야
합니다. 프론트 개발 주소는 백엔드 `.env`의 `ALLOWED_ORIGINS`에 정확히 등록해야 합니다.

상세 클라이언트 예제와 오류 처리 방식은
[프론트엔드 API 연동 안내](docs/backend/FRONTEND_API_HANDOFF.md)를 확인합니다.

## DB만 실행

애플리케이션을 IDE나 Gradle로 직접 실행하면서 DB만 Docker로 사용할 수도 있습니다.

```bash
cp .env.example .env
docker compose up -d --wait db
docker compose run --rm migrate
docker compose run --rm --no-deps ingest

cd backend
./gradlew bootRun
```

이 방식에만 호스트 Java 17이 필요합니다. 애플리케이션 JDBC 기본 주소는
`jdbc:postgresql://localhost:15432/pl_timetable`입니다.

로컬 DB를 완전히 삭제하려면 다음 명령을 사용합니다.

```bash
docker compose down -v
```

이 명령은 Docker named volume의 DB 데이터를 삭제하므로 필요한 경우 먼저
`./scripts/backup-database.sh`를 실행합니다.

## 검증

```bash
cd backend
./gradlew clean build
```

통합 테스트는 Testcontainers PostgreSQL 18.4 빈 DB에 전체 Flyway 마이그레이션을
적용합니다. Compose와 셸 스크립트는 다음처럼 검증할 수 있습니다.

```bash
docker compose config --quiet
bash -n scripts/*.sh
```

## 문서

- [외부 API 문서(Scalar)](https://timetable-api.kdhoon.me/)
- [Swagger UI](https://timetable-api.kdhoon.me/swagger-ui.html)
- [전체 백엔드 API 명세](docs/backend/API_REFERENCE.md)
- [프론트엔드 API 연동 안내](docs/backend/FRONTEND_API_HANDOFF.md)
- [OpenAPI·API 문서 사용법](docs/backend/OPENAPI.md)
- [인증·사용자 API](docs/backend/AUTH_USER_API.md)
- [학사 조회 API](docs/backend/ACADEMIC_API.md)
- [이수과목 API](docs/backend/COMPLETED_COURSE_API.md)
- [졸업요건 API](docs/backend/GRADUATION_API.md)
- [시간표 API](docs/backend/TIMETABLE_API.md)
- [자동 편성 API](docs/backend/OPTIMIZATION_API.md)
- [백엔드 구조 원칙](docs/backend/ARCHITECTURE.md)
- [구현 완료·미구현 상태](docs/database/STATUS.md)
- [DB 구조와 데이터 적재](docs/database/README.md)
- [전체 ERD 시각화](docs/database/ERD.html)
- [DB 규칙과 변경 절차](docs/database/CONVENTIONS.md)
- [데이터 범위·출처·완전성](docs/database/DATASET.md)
- [학교 서버 배포 런북](docs/deployment/SCHOOL_SERVER.md)
- [DB 백업·복구](docs/deployment/BACKUP_RESTORE.md)
