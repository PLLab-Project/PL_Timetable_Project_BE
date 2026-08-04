#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

for command_name in docker tar; do
    command -v "$command_name" >/dev/null 2>&1 || {
        echo "필수 명령을 찾을 수 없습니다: $command_name" >&2
        exit 1
    }
done
docker compose version >/dev/null

./scripts/prepare-academic-data.sh

echo "현재 Compose 프로젝트의 로컬 PostgreSQL과 학사 기준 데이터만 준비합니다."
echo "API는 빌드하거나 시작·재시작하지 않으며 외부 DB에는 연결하지 않습니다."
echo "새 데이터 패키지를 기존 로컬 DB에 넣는 경우에는 먼저 필요한 사용자 데이터를 백업하세요."
docker compose up --detach --wait --wait-timeout 180 db
docker compose run --rm migrate
docker compose run --rm --no-deps ingest

echo
docker compose ps db
echo
echo "DB 스키마 마이그레이션, 학사 데이터 적재와 무결성 검증을 완료했습니다."
