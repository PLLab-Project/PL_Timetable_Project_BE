#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for command_name in docker git; do
    command -v "$command_name" >/dev/null 2>&1 || {
        echo "필수 명령을 찾을 수 없습니다: $command_name" >&2
        exit 1
    }
done
docker compose version >/dev/null

if [[ ! -f .env ]]; then
    echo ".env가 없습니다." >&2
    echo "cp .env.school.example .env 후 운영 값을 입력하세요." >&2
    exit 1
fi

required_payloads=(
    data/database/SHA256SUMS
    data/database/expected-row-counts.tsv
    data/database/current-catalog.sql.gz
    data/database/reference-data.sql.gz.part-00
    data/database/reference-data.sql.gz.part-01
    data/database/reference-data.sql.gz.part-02
)
for payload in "${required_payloads[@]}"; do
    [[ -f "$payload" ]] || {
        echo "학사 데이터 파일이 없습니다: $payload" >&2
        exit 1
    }
done

env_value() {
    local key="$1"
    sed -n "s/^${key}=//p" .env | tail -n 1
}

profile="$(env_value SPRING_PROFILES_ACTIVE)"
database_password="$(env_value POSTGRES_PASSWORD)"
otp_delivery="$(env_value OTP_DELIVERY)"
if [[ "$profile" == "prod" ]] \
    && [[ -z "$database_password" \
        || "$database_password" == "local-only-change-me" \
        || "$database_password" == CHANGE_ME* ]]; then
    echo "운영 환경에서는 안전한 POSTGRES_PASSWORD가 필요합니다." >&2
    exit 1
fi

if [[ "$profile" == "prod" && "$(env_value SESSION_COOKIE_SECURE)" != "true" ]]; then
    echo "운영 환경에서는 SESSION_COOKIE_SECURE=true여야 합니다." >&2
    exit 1
fi

if [[ "$profile" == "prod" && "$otp_delivery" != "smtp" ]]; then
    echo "운영 환경에서는 OTP_DELIVERY=smtp여야 합니다. OTP를 로그에 출력하지 않습니다." >&2
    exit 1
fi

if [[ "$otp_delivery" == "smtp" ]]; then
    for key in SMTP_HOST SMTP_USERNAME SMTP_PASSWORD OTP_FROM; do
        value="$(env_value "$key")"
        if [[ -z "$value" || "$value" == *example* ]]; then
            echo "SMTP 전송에 사용할 실제 ${key} 값을 .env에 입력하세요." >&2
            exit 1
        fi
    done
fi

export APP_COMMIT="${APP_COMMIT:-$(git rev-parse --short=12 HEAD)}"
export APP_VERSION="${APP_VERSION:-$(env_value APP_VERSION)}"
export APP_VERSION="${APP_VERSION:-0.0.1-SNAPSHOT}"
export APP_IMAGE_TAG="${APP_IMAGE_TAG:-$APP_COMMIT}"

echo "PL Timetable API를 빌드하고 시작합니다."
echo "version=$APP_VERSION commit=$APP_COMMIT"
docker compose up --detach --wait --wait-timeout 180 db
docker compose run --rm migrate
docker compose run --rm --no-deps ingest
docker compose up --detach --build --no-deps --wait --wait-timeout 600 api

echo
docker compose ps
echo
docker compose exec -T api \
    curl --fail --silent --show-error \
    http://127.0.0.1:8080/api/v1/health/live
echo
