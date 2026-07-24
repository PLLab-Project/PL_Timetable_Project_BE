# 학교 서버 배포 런북

이 문서는 새 학교 컴퓨터에서 호스트 Java·Gradle·PostgreSQL 설치 없이 PL Timetable
백엔드를 실행하고 업데이트하는 절차입니다.

## 배포 구조

```text
프론트 브라우저
  └─ HTTPS reverse proxy 또는 Cloudflare Tunnel
       └─ API 호스트 포트 18082
            └─ Docker api:8080 (Spring Boot + JRE 17)
                 └─ Docker db:5432 (PostgreSQL 18.4)
```

Compose 서비스:

| 서비스 | 역할 | 상시 실행 |
|---|---|---:|
| `db` | PostgreSQL 18.4와 영구 named volume | 예 |
| `migrate` | Flyway 스키마 적용 | 아니요 |
| `ingest` | 기준 데이터 체크섬 검증·멱등 적재 | 아니요 |
| `api` | Spring Boot, OpenAPI, Scalar, OR-Tools | 예 |

API 이미지는 멀티 스테이지로 빌드합니다. 첫 단계의 JDK 17과 Gradle Wrapper가 JAR를
만들고, 최종 이미지에는 JRE 17과 실행 JAR만 들어갑니다. 호스트 Java 설치는 필요하지
않습니다.

## 1. 사전 준비

- 64비트 Linux 권장
- Docker Engine
- Docker Compose v2
- Git
- 최소 권장 여유 공간 10GB
- 운영 시 HTTPS 도메인 또는 터널

Docker가 부팅 시 자동 시작되도록 설정합니다.

```bash
sudo systemctl enable --now docker
docker version
docker compose version
```

## 2. 저장소와 학사 데이터

```bash
git clone https://github.com/PLLab-Project/PL_Timetable_Project_BE.git
cd PL_Timetable_Project_BE
git switch main
git pull --ff-only origin main
```

공개 Git에서 제외된 다음 단일 파일을 승인된 경로로 전달받아 `data/database/`에 둡니다.

```text
academic-data-bundle.tar.gz
```

`SHA256SUMS`, `expected-row-counts.tsv`, `manifest.json`은 저장소에 포함되어 있습니다.
실행 스크립트가 번들의 파일 구성을 제한하고 자동으로 푼 뒤 조각별·전체 체크섬을
확인하므로 파일이 손상되거나 다른 버전이면 실행을 중단합니다.

## 3. 운영 환경변수

```bash
cp .env.school.example .env
chmod 600 .env
```

반드시 변경할 값:

| 환경변수 | 설명 |
|---|---|
| `POSTGRES_PASSWORD` | 길고 임의적인 운영 DB 비밀번호 |
| `ALLOWED_ORIGINS` | 쉼표로 구분한 실제 프론트 Origin과 로컬 개발 Origin |
| `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` | OTP 메일 발송 계정 |
| `OTP_FROM` | 발신 주소 |
| `API_BIND_ADDRESS` | 프록시가 접근할 호스트 인터페이스 |

API와 프론트가 `api.example.com`, `app.example.com`처럼 같은 사이트의 서브도메인이면
다음 기본값을 사용합니다.

```env
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=lax
```

서로 다른 최상위 사이트에서 쿠키를 사용해야 한다면 HTTPS를 전제로 다음 값을 사용합니다.

```env
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
```

프론트 JavaScript는 다른 API 호스트의 쿠키를 직접 읽지 않습니다.
`GET /api/v1/auth/csrf` 응답의 `data.token`을 메모리에 저장해 상태 변경 요청의
`X-XSRF-TOKEN` 헤더로 보냅니다.

## 4. 최초 실행

```bash
./start.sh
```

스크립트는 다음을 자동 수행합니다.

1. 필수 프로그램·환경파일·학사 데이터 파일 확인
2. 운영 비밀번호와 보안 쿠키 설정 검사
3. 현재 Git 커밋을 이미지와 health 응답에 기록
4. PostgreSQL healthcheck 대기
5. Flyway 마이그레이션을 업데이트 때도 매번 명시적으로 실행
6. 기준 데이터 체크섬 검증·멱등 적재를 업데이트 때도 매번 명시적으로 실행
7. API 이미지 빌드·실행
8. API healthcheck

상태 확인:

```bash
docker compose ps
docker compose logs --tail=200 api
docker compose exec -T api \
  curl -fsS http://127.0.0.1:8080/api/v1/health/live
```

정상 응답의 `data.commit`은 배포한 Git 커밋입니다.

## 5. 외부 공개

기본 포트는 `127.0.0.1:18082`이므로 인터넷에 직접 공개되지 않습니다. Nginx, Caddy
또는 Cloudflare Tunnel이 HTTPS를 종료하고 이 포트로 전달하도록 구성합니다.

Cloudflare Tunnel이 별도 Docker 컨테이너에서 호스트로 접근하는 예:

```env
API_BIND_ADDRESS=172.17.0.1
API_PORT=18082
SERVER_FORWARD_HEADERS_STRATEGY=framework
```

```yaml
ingress:
  - hostname: timetable-api.example.com
    service: http://host.docker.internal:18082
```

DB 포트 `15432`는 계속 `127.0.0.1`에만 바인딩합니다.

외부 검증:

```bash
curl -fsS https://timetable-api.example.com/api/v1/health/live
curl -fsS https://timetable-api.example.com/v3/api-docs
```

## 6. 프론트 CORS 확인

```bash
curl -i -X OPTIONS \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: GET' \
  https://timetable-api.example.com/api/v1/semesters
```

허용된 Origin이면 `Access-Control-Allow-Origin`과
`Access-Control-Allow-Credentials: true`가 반환됩니다. 와일드카드 Origin은 세션
쿠키와 함께 사용하지 않습니다.

## 7. 업데이트

업데이트 전에 DB를 백업합니다.

```bash
./scripts/backup-database.sh
git switch main
git pull --ff-only origin main
./start.sh
```

최신 커밋 확인:

```bash
expected="$(git rev-parse --short=12 HEAD)"
actual="$(curl -fsS http://127.0.0.1:18082/api/v1/health/live \
  | sed -n 's/.*\"commit\":\"\\([^\"]*\\)\".*/\\1/p')"
test "$expected" = "$actual"
```

Flyway 마이그레이션은 앞으로만 진행되므로 파괴적인 스키마 변경 전에는 백업과 복구
시험이 필요합니다.

## 8. 중지·재시작·로그

```bash
docker compose restart api
docker compose logs --follow api
docker compose stop
docker compose start
```

DB 볼륨을 유지한 채 컨테이너만 내립니다.

```bash
docker compose down
```

다음 명령은 DB 볼륨을 삭제하므로 백업 없이는 실행하지 않습니다.

```bash
docker compose down -v
```

## 9. 롤백

1. 현재 DB를 백업합니다.
2. 문제가 발생한 변경에 DB 마이그레이션이 없다면 이전 Git 커밋으로 이동합니다.
3. 동일한 부트스트랩 명령으로 이전 이미지를 다시 빌드합니다.

```bash
git switch --detach <known-good-commit>
./start.sh
```

DB 마이그레이션이 포함된 변경은 애플리케이션만 이전 버전으로 돌렸을 때 호환되지 않을 수
있습니다. 이 경우 [DB 백업·복구](BACKUP_RESTORE.md)에 따라 검증된 백업을 복원합니다.

## 배포 완료 기준

- `docker compose ps`에서 `db`, `api`가 healthy
- health 응답 `data.commit`이 배포 대상 Git 커밋과 동일
- 외부 Scalar·OpenAPI 200
- 실제 프론트 Origin의 CORS preflight 성공
- SMTP OTP 수신·검증과 세션 쿠키 생성 성공
- 보호된 POST 요청에서 CSRF 토큰 사용 성공
- 재부팅 후 컨테이너 자동 복구 확인
- 최신 DB 백업 파일의 복원 시험 완료
