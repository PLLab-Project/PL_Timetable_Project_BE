#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

for command_name in docker git tar; do
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

payload_directory="data/database"
payload_bundle="${payload_directory}/academic-data-bundle.tar.gz"
payload_files=(
    current-catalog.sql.gz
    reference-data.sql.gz.part-00
    reference-data.sql.gz.part-01
    reference-data.sql.gz.part-02
)

missing_payload=false
for payload in "${payload_files[@]}"; do
    [[ -f "${payload_directory}/${payload}" ]] || missing_payload=true
done

if [[ "$missing_payload" == true ]]; then
    if [[ ! -f "$payload_bundle" ]]; then
        echo "학사 데이터 번들이 없습니다: $payload_bundle" >&2
        echo "전달받은 단일 번들을 위 경로에 배치하세요." >&2
        exit 1
    fi

    expected_entries="$(printf '%s\n' "${payload_files[@]}" | LC_ALL=C sort)"
    actual_entries="$(
        tar -tzf "$payload_bundle" \
            | sed 's#^\./##' \
            | sed '/\/$/d' \
            | LC_ALL=C sort
    )"
    if [[ "$actual_entries" != "$expected_entries" ]]; then
        echo "학사 데이터 번들의 파일 구성이 올바르지 않습니다." >&2
        exit 1
    fi

    echo "단일 학사 데이터 번들을 풉니다: $payload_bundle"
    tar -xzf "$payload_bundle" \
        -C "$payload_directory" \
        --no-same-owner \
        --no-same-permissions \
        "${payload_files[@]}"
fi

required_payloads=(
    "${payload_directory}/SHA256SUMS"
    "${payload_directory}/expected-row-counts.tsv"
)
for payload in "${payload_files[@]}"; do
    required_payloads+=("${payload_directory}/${payload}")
done
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
