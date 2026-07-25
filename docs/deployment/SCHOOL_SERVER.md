# Windows 학교 서버 배포 런북

이 문서는 **Windows 10/11 64비트 학교 컴퓨터**에서 호스트 Java·Gradle·PostgreSQL
설치 없이 PL Timetable 백엔드를 실행하고 업데이트하는 절차입니다. Docker Desktop의
WSL2 엔진으로 Linux 컨테이너를 실행하며, 명령은 WSL Ubuntu 터미널에서 수행합니다.

> Docker Desktop은 Windows Server 2019·2022 같은 Windows Server 제품을 공식
> 지원하지 않습니다. 학교 컴퓨터가 Windows Server라면 이 절차를 사용하지 말고
> Linux VM 또는 학교 인프라 담당자가 지원하는 컨테이너 호스트를 사용해야 합니다.
> 지원 Windows 버전과 요구사항은
> [Docker Desktop Windows 설치 문서](https://docs.docker.com/desktop/setup/install/windows-install/)를
> 기준으로 확인합니다.

## 배포 구조

```text
프론트 브라우저
  └─ HTTPS Cloudflare Tunnel
       └─ Windows cloudflared 서비스
            └─ Windows 127.0.0.1:18082
                 └─ Docker Desktop WSL2
                      ├─ api:8080 (Spring Boot + JRE 17)
                      └─ db:5432 (PostgreSQL 18.4)
```

Compose 서비스:

| 서비스 | 역할 | 상시 실행 |
|---|---|---:|
| `db` | PostgreSQL 18.4와 영구 named volume | 예 |
| `migrate` | Flyway 스키마 적용 | 아니요 |
| `ingest` | 기준 데이터 체크섬 검증·멱등 적재 | 아니요 |
| `api` | Spring Boot, OpenAPI, Scalar, OR-Tools | 예 |

API 이미지는 멀티 스테이지로 빌드합니다. 빌드 이미지의 JDK 17과 Gradle Wrapper가
JAR를 만들고 최종 이미지에는 JRE 17과 실행 JAR만 들어갑니다. Windows 호스트에
Java·Gradle·PostgreSQL을 따로 설치하지 않습니다.

## 1. 설치 전 확인

필수 조건:

- 지원 중인 Windows 10/11 64비트
- BIOS/UEFI 가상화 활성화
- 관리자 권한
- WSL 2
- Docker Desktop
- 최소 8GB RAM, 권장 여유 공간 10GB 이상
- 학교 네트워크의 외부 방향 TCP `443`, TCP·UDP `7844` 허용
- Cloudflare에서 관리하는 도메인과 Tunnel 생성 권한

Windows에서 `winver`를 실행해 버전을 확인합니다. PowerShell을 관리자 권한으로 열고
WSL을 설치합니다.

```powershell
wsl --install -d Ubuntu
wsl --update
wsl --version
```

처음 설치한 경우 Windows를 재부팅하고 Ubuntu를 한 번 실행해 Linux 사용자 이름과
비밀번호를 만듭니다.

## 2. Docker Desktop과 WSL2 설정

PowerShell에서 Docker Desktop을 설치할 수 있습니다.

```powershell
winget install --exact --id Docker.DockerDesktop
```

Docker Desktop을 실행한 뒤 다음 설정을 확인합니다.

1. **Settings → General → Use the WSL 2 based engine**
2. **Settings → Resources → WSL Integration → Ubuntu 활성화**
3. **Settings → General → Start Docker Desktop when you sign in**

Ubuntu 터미널에서 Docker와 Compose가 보이는지 확인합니다.

```bash
docker version
docker compose version
```

명령을 찾지 못하면 Docker Desktop이 실행 중인지, Ubuntu WSL Integration이
활성화됐는지 먼저 확인합니다.

Docker Desktop WSL2는 Windows 사용자 세션에서 실행됩니다. 컴퓨터 재부팅 후 아무도
로그인하지 않아도 항상 API가 자동 복구되어야 하는 운영 조건이라면 Docker Desktop
대신 Linux VM을 사용하는 편이 안전합니다.

## 3. 저장소 받기

성능과 Linux 파일 권한을 위해 저장소는 `C:\...` 또는 `/mnt/c/...`가 아니라 WSL
사용자 홈에 둡니다.

```bash
sudo apt-get update
sudo apt-get install -y git curl ca-certificates nano

mkdir -p ~/services
cd ~/services
git clone https://github.com/PLLab-Project/PL_Timetable_Project_BE.git
cd PL_Timetable_Project_BE
git switch main
git pull --ff-only origin main
```

## 4. 학사 데이터 파일 배치

공개 Git에 포함하지 않는 다음 **단일 파일 하나**를 담당자에게 전달받습니다.

```text
academic-data-bundle.tar.gz
```

권장 전달 방법:

- 담당자가 직접 연결한 저장장치
- 접근 대상을 제한한 학교·팀 파일 전달 수단
- 짧은 만료시간을 가진 일회성 다운로드 링크

지속적인 GitHub Deploy Key나 클라우드 서비스 계정은 설치 한 대와 드문 데이터 갱신
환경에서는 관리 비용과 장기 자격증명 위험이 더 큽니다. 자동 원격 갱신이 실제로
필요해질 때 별도로 도입합니다.

Windows의 다운로드 폴더에 파일이 있다면 WSL에서 다음처럼 복사합니다.

```bash
mkdir -p data/database
cp "/mnt/c/Users/<WINDOWS_USER>/Downloads/academic-data-bundle.tar.gz" \
  data/database/academic-data-bundle.tar.gz
```

또는 WSL 저장소 폴더를 Windows 탐색기로 열어 파일을 넣을 수 있습니다.

```bash
explorer.exe data/database
```

부트스트랩은 압축 내부 파일 목록을 제한하고, Git에 포함된 `SHA256SUMS`로 각 SQL
파일을 검증한 뒤 `expected-row-counts.tsv`의 기대 행 수까지 확인합니다. 손상되거나
다른 버전의 파일이면 DB 적재 전에 중단합니다.

## 5. 운영 환경과 난수 DB 비밀번호

`.env.school.example`을 직접 복사해 비밀번호를 사람이 만들지 않습니다. 다음 스크립트가
운영 템플릿에서 `.env`를 만들고 `/dev/urandom` 기반 256비트 DB 비밀번호를 자동으로
설정합니다.

```bash
./scripts/initialize-school-env.sh
```

안전 동작:

- 32바이트 난수를 64자리 소문자 hex로 저장
- 생성된 비밀번호를 터미널에 출력하지 않음
- `.env` 권한을 현재 WSL 사용자만 읽을 수 있는 `600`으로 설정
- 기존 `.env`가 있으면 덮어쓰지 않고 중단
- `.env`는 `.gitignore`로 공개 Git에서 제외

생성 후 다음 값을 실제 운영 환경에 맞게 수정합니다.

```bash
nano .env
```

| 환경변수 | 설정 |
|---|---|
| `ALLOWED_ORIGINS` | 실제 프론트 Origin과 필요한 로컬 개발 Origin |
| `SMTP_HOST` | OTP 발송 SMTP 서버 |
| `SMTP_USERNAME` | OTP 발송 계정 |
| `SMTP_PASSWORD` | SMTP 비밀번호 또는 앱 비밀번호 |
| `OTP_FROM` | 실제 발신 주소 |
| `SCHOOL_EMAIL_DOMAIN` | 허용할 학교 이메일 도메인 |

Tunnel이 같은 Windows 컴퓨터의 API로 연결하므로 다음 값은 유지합니다.

```env
API_BIND_ADDRESS=127.0.0.1
API_PORT=18082
SERVER_FORWARD_HEADERS_STRATEGY=framework
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=lax
```

`POSTGRES_PASSWORD`를 다시 생성하려고 `.env`를 삭제하면 안 됩니다. 이미 만들어진
PostgreSQL 볼륨의 비밀번호는 새 `.env`를 만든다고 자동 변경되지 않습니다. `.env`는
Git이 아닌 승인된 암호 저장소에 별도로 백업합니다.

## 6. 최초 실행

Ubuntu 터미널에서 실행합니다.

```bash
./start.sh
```

스크립트 하나가 다음 작업을 수행합니다.

1. Docker·Git·데이터 번들·운영 설정 검사
2. 단일 데이터 번들 해제와 파일별 SHA-256 검증
3. PostgreSQL 18.4 시작
4. Flyway 마이그레이션 적용
5. 기준 데이터 멱등 적재와 기대 행 수 검증
6. Spring Boot Docker 이미지 빌드
7. API 시작과 healthcheck

로컬 확인:

```bash
curl -fsS http://127.0.0.1:18082/api/v1/health/live
curl -fsS http://127.0.0.1:18082/v3/api-docs
```

브라우저에서 `http://127.0.0.1:18082/`을 열면 Scalar API 문서가 표시됩니다.

## 7. Cloudflare Tunnel로 외부 공개

API와 DB 포트를 공유기나 Windows 방화벽에서 직접 개방하지 않습니다. `cloudflared`가
Cloudflare로 외부 방향 연결을 만들고 `127.0.0.1:18082`로 전달합니다.

### 7.1 Tunnel과 공개 호스트 생성

Cloudflare Zero Trust Dashboard에서 다음 순서로 설정합니다.

1. **Networks → Connectors → Cloudflare Tunnels**
2. 팀 전용 Tunnel 생성
3. Connector 운영체제로 Windows 선택
4. Dashboard가 표시하는 서비스 설치 명령의 `<TUNNEL_TOKEN>` 확인
5. Public Hostname 추가

Public Hostname의 Service:

```text
http://localhost:18082
```

DB 포트 `15432`는 Tunnel Route에 등록하지 않습니다.

### 7.2 cloudflared Windows 서비스 설치

관리자 PowerShell에서 설치합니다.

```powershell
winget install --exact --id Cloudflare.cloudflared
cloudflared.exe service install <TUNNEL_TOKEN>
Get-Service *cloudflared*
```

서비스 시작 유형과 상태를 확인합니다.

```powershell
Set-Service -Name cloudflared -StartupType Automatic
Start-Service cloudflared
Get-Service cloudflared
```

Tunnel 토큰은 저장소, `.env`, 메신저 또는 문서에 기록하지 않습니다. 노출되면
Cloudflare Dashboard에서 즉시 교체합니다.

설치 명령이나 Windows 서비스 동작이 변경된 경우
[Cloudflare Tunnel 설정 문서](https://developers.cloudflare.com/tunnel/setup/)와
[Windows 서비스 문서](https://developers.cloudflare.com/tunnel/advanced/local-management/as-a-service/windows/)를
우선합니다.

### 7.3 외부 확인

학교 컴퓨터가 아닌 휴대전화 LTE 또는 다른 외부 네트워크에서 확인합니다.

```bash
curl -fsS https://timetable-api.example.com/api/v1/health/live
curl -fsS https://timetable-api.example.com/v3/api-docs
```

정상 조건:

- health 응답의 `data.commit`이 배포한 Git 커밋과 같음
- OpenAPI의 `servers[0].url`이 외부 HTTPS API 주소
- 브라우저에서 외부 루트 주소를 열면 Scalar 표시
- Cloudflare Dashboard에서 Connector가 Healthy

## 8. 프론트 CORS와 쿠키

프론트 환경변수:

```env
VITE_API_BASE_URL=https://timetable-api.example.com
```

백엔드 `.env`:

```env
ALLOWED_ORIGINS=https://frontend.example.com,http://localhost:5173
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=lax
```

허용 Origin 확인:

```bash
curl -i -X OPTIONS \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: GET' \
  https://timetable-api.example.com/api/v1/semesters
```

응답에 요청 Origin과 `Access-Control-Allow-Credentials: true`가 포함되어야 합니다.
세션 쿠키를 사용하므로 `ALLOWED_ORIGINS=*`는 허용하지 않습니다.

## 9. 재부팅 검증

설치를 완료한 뒤 Windows를 실제로 한 번 재부팅해 자동 복구를 확인합니다.

1. Windows 사용자 로그인
2. Docker Desktop 실행 상태 확인
3. 관리자 PowerShell에서 `Get-Service cloudflared`
4. Ubuntu 터미널에서 서비스와 health 확인

```bash
cd ~/services/PL_Timetable_Project_BE
docker compose ps
curl -fsS http://127.0.0.1:18082/api/v1/health/live
```

Docker Desktop이 시작된 뒤 Compose의 `db`와 `api`는 `restart: unless-stopped` 정책으로
복구됩니다. Windows 로그인 없이도 반드시 자동 복구되어야 한다면 Linux VM 전환을
운영 요구사항으로 잡습니다.

## 10. 업데이트

Ubuntu 터미널에서 DB를 먼저 백업한 뒤 코드를 업데이트합니다.

```bash
cd ~/services/PL_Timetable_Project_BE
./scripts/backup-database.sh
git switch main
git pull --ff-only origin main
./start.sh
```

학사 데이터가 변경된 경우에만 담당자가 새 `academic-data-bundle.tar.gz`를 전달합니다.
기존 파일과 DB 백업을 보존한 상태에서 새 번들을 배치하고 `./start.sh`를 실행합니다.

## 11. 중지·재시작·로그

Ubuntu:

```bash
docker compose ps
docker compose logs --follow api
docker compose restart api
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

Windows 관리자 PowerShell:

```powershell
Get-Service cloudflared
Restart-Service cloudflared
```

## 12. 문제 해결

| 증상 | 확인 |
|---|---|
| `docker` 명령을 찾지 못함 | Docker Desktop 실행 및 Ubuntu WSL Integration |
| Docker가 시작되지 않음 | BIOS 가상화, WSL 버전, Docker Desktop 상태 |
| 데이터 번들 오류 | 파일명, 압축 내부 목록, Git의 `SHA256SUMS` |
| DB 비밀번호 오류 | 기존 `.env` 복구, 새 비밀번호 임의 생성 금지 |
| 로컬 API가 열리지 않음 | `docker compose ps`, `docker compose logs api` |
| Cloudflare 502 | 먼저 로컬 `127.0.0.1:18082` health 확인 |
| 외부 주소가 열리지 않음 | `Get-Service cloudflared`, Dashboard Connector 상태 |
| Tunnel 연결 반복 실패 | 학교망 외부 방향 TCP·UDP `7844`, TCP `443` |
| 프론트 CORS 실패 | 실제 Origin과 `ALLOWED_ORIGINS`의 완전 일치 |
| 세션 쿠키가 전송되지 않음 | HTTPS, Secure, SameSite, `credentials: "include"` |

## 13. 완료 체크리스트

- [ ] Windows 10/11, WSL2, Docker Desktop 버전 확인
- [ ] 저장소를 WSL 홈에 clone
- [ ] 단일 학사 데이터 번들 배치
- [ ] 난수 비밀번호 기반 `.env` 생성 및 SMTP·프론트 주소 설정
- [ ] 저장소의 전체 Flyway 마이그레이션 적용
- [ ] 학사 데이터 체크섬·기대 행 수 검증
- [ ] 로컬 health·OpenAPI·Scalar 확인
- [ ] cloudflared Windows 서비스 자동 시작
- [ ] 외부 HTTPS health·OpenAPI 확인
- [ ] Windows 재부팅 후 자동 복구 시험
- [ ] DB 백업·복구 절차 확인

관련 문서:

- [백업·복구](BACKUP_RESTORE.md)
- [DB 구조·적재](../database/README.md)
- [프론트 연결](../../FRONTEND.md)
- [OpenAPI 사용법](../backend/OPENAPI.md)
