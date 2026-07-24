#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

mkdir -p backups
timestamp="$(date +%Y%m%d_%H%M%S)"
output="${1:-backups/pl_timetable_${timestamp}.dump}"
mkdir -p "$(dirname "$output")"

docker compose exec -T db sh -c \
    'pg_dump --format=custom --no-owner --username="$POSTGRES_USER" "$POSTGRES_DB"' \
    >"$output"

test -s "$output"
echo "DB 백업 완료: $output"
