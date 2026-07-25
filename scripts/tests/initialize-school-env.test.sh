#!/usr/bin/env bash
set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_SCRIPT="${SOURCE_ROOT}/scripts/initialize-school-env.sh"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

[[ -x "$TARGET_SCRIPT" ]] || {
    echo "초기화 스크립트를 실행할 수 없습니다: $TARGET_SCRIPT" >&2
    exit 1
}

mkdir -p "$FIXTURE_ROOT/scripts"
cp "$SOURCE_ROOT/.env.school.example" "$FIXTURE_ROOT/.env.school.example"
cp "$TARGET_SCRIPT" "$FIXTURE_ROOT/scripts/initialize-school-env.sh"

first_output="$(
    cd "$FIXTURE_ROOT"
    ./scripts/initialize-school-env.sh
)"

password="$(sed -n 's/^POSTGRES_PASSWORD=//p' "$FIXTURE_ROOT/.env")"
[[ "$password" =~ ^[0-9a-f]{64}$ ]] || {
    echo "DB 비밀번호가 256비트 hex 형식이 아닙니다." >&2
    exit 1
}
[[ "$first_output" != *"$password"* ]] || {
    echo "생성된 DB 비밀번호가 표준 출력에 노출되었습니다." >&2
    exit 1
}
[[ "$(stat -c '%a' "$FIXTURE_ROOT/.env")" == "600" ]] || {
    echo ".env 권한이 600이 아닙니다." >&2
    exit 1
}
grep -q '^SPRING_PROFILES_ACTIVE=prod$' "$FIXTURE_ROOT/.env"

before_checksum="$(sha256sum "$FIXTURE_ROOT/.env" | cut -d' ' -f1)"
if (
    cd "$FIXTURE_ROOT"
    ./scripts/initialize-school-env.sh >/dev/null 2>&1
); then
    echo "기존 .env를 덮어쓰지 않고 실패해야 합니다." >&2
    exit 1
fi
after_checksum="$(sha256sum "$FIXTURE_ROOT/.env" | cut -d' ' -f1)"
[[ "$before_checksum" == "$after_checksum" ]] || {
    echo "재실행이 기존 .env를 변경했습니다." >&2
    exit 1
}

echo "initialize-school-env: PASS"
