# PL Timetable Backend

대진대학교 시간표·학사·졸업요건 팀 프로젝트의 Spring Boot 백엔드입니다.
PostgreSQL 18.4, Flyway, 세션 인증, 강의·리뷰·이수과목·졸업요건, 시간표와
OR-Tools 자동편성 API를 제공합니다.

## 가장 간단한 실행

호스트에는 **Docker Engine, Docker Compose v2, Git만 필요**합니다.
Java, Gradle, PostgreSQL은 따로 설치하지 않습니다.

### 1. 저장소 받기

```bash
git clone https://github.com/PLLab-Project/PL_Timetable_Project_BE.git
cd PL_Timetable_Project_BE
```

### 2. 학사 데이터 한 파일 넣기

팀에서 전달받은 다음 파일 하나를 `data/database/`에 넣습니다.

```text
academic-data-bundle.tar.gz
```

이 파일은 교수명과 원천 학사 payload를 포함할 수 있어 공개 Git에는 올리지 않습니다.
실행 스크립트가 번들을 자동으로 풀고 내부 파일의 SHA-256과 기대 행 수를 검증합니다.

### 3. 실행하기

로컬 개발:

```bash
./start.sh
```

`.env`가 없으면 로컬 개발용 설정을 자동 생성합니다.

학교 서버:

```bash
cp .env.school.example .env
# .env의 DB 비밀번호, 프론트 주소와 SMTP 설정을 실제 값으로 수정
./start.sh
```

스크립트 하나가 다음 작업을 모두 수행합니다.

1. PostgreSQL 18.4 시작
2. Flyway 마이그레이션
3. 학사 데이터 번들 해제·검증·멱등 적재
4. Spring Boot Docker 이미지 빌드
5. API 시작과 healthcheck

## 실행 주소

| 용도 | 기본 주소 |
|---|---|
| API | `http://127.0.0.1:18082` |
| Scalar API 문서 | `http://127.0.0.1:18082/` |
| Swagger UI | `http://127.0.0.1:18082/swagger-ui.html` |
| OpenAPI JSON | `http://127.0.0.1:18082/v3/api-docs` |
| 상태·배포 커밋 | `http://127.0.0.1:18082/api/v1/health/live` |

현재 팀 테스트 서버는 [timetable-api.kdhoon.me](https://timetable-api.kdhoon.me/)입니다.

## 프론트엔드 연결

프론트 개발자는 **[FRONTEND.md](FRONTEND.md) 문서 하나부터 확인하면 됩니다.**
API 주소, 세션·CSRF 처리, 공통 응답, 오류 분기와 재사용 가능한 TypeScript
`fetch` 래퍼가 한 문서에 정리되어 있습니다.

실행 중인 Scalar 문서에서는 모든 요청·응답 스키마와 예제를 확인하고 직접 요청할 수
있습니다.

## 업데이트

```bash
git switch main
git pull --ff-only origin main
./scripts/backup-database.sh
./start.sh
```

## 주요 문서

- [프론트엔드 연결 가이드](FRONTEND.md)
- [실행 중인 API 명세](https://timetable-api.kdhoon.me/)
- [구현 완료·미구현 상태](docs/database/STATUS.md)
- [DB 구조와 데이터 적재](docs/database/README.md)
- [전체 ERD](docs/database/ERD.html)
- [학교 서버 배포 런북](docs/deployment/SCHOOL_SERVER.md)
- [DB 백업·복구](docs/deployment/BACKUP_RESTORE.md)

## 데이터 번들 생성

기존 원본 학사 데이터 파일을 가진 담당자는 다음 명령으로 전달용 단일 파일을 만듭니다.

```bash
./scripts/package-academic-data.sh
```

생성 결과:

```text
data/database/academic-data-bundle.tar.gz
```
