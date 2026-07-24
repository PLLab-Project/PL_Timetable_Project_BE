#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
    cp .env.example .env
    echo "로컬 개발용 .env를 자동 생성했습니다."
fi

exec ./scripts/bootstrap-school.sh "$@"
