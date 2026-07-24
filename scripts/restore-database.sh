#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

backup="${1:-}"
if [[ -z "$backup" || ! -f "$backup" ]]; then
    echo "사용법: CONFIRM_RESTORE=pl_timetable $0 <backup.dump>" >&2
    exit 1
fi
if [[ "${CONFIRM_RESTORE:-}" != "pl_timetable" ]]; then
    echo "복구는 기존 DB 내용을 덮어쓸 수 있습니다." >&2
    echo "CONFIRM_RESTORE=pl_timetable을 지정해야 실행됩니다." >&2
    exit 1
fi

docker compose stop api
docker compose exec -T db sh -c \
    'pg_restore --clean --if-exists --no-owner --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
    <"$backup"
docker compose start api
docker compose exec -T api \
    curl --fail --silent --show-error \
    http://127.0.0.1:8080/api/v1/health/live
echo
echo "DB 복구 완료: $backup"
