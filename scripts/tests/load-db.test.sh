#!/usr/bin/env bash
set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_SCRIPT="${SOURCE_ROOT}/load-db.sh"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

[[ -x "$TARGET_SCRIPT" ]] || {
    echo "DB 전용 적재 스크립트를 실행할 수 없습니다: $TARGET_SCRIPT" >&2
    exit 1
}

mkdir -p "$FIXTURE_ROOT/scripts" "$FIXTURE_ROOT/mock-bin"
cp "$TARGET_SCRIPT" "$FIXTURE_ROOT/load-db.sh"

cat >"$FIXTURE_ROOT/scripts/prepare-academic-data.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'prepared\n' >prepare.marker
EOF
chmod 755 "$FIXTURE_ROOT/scripts/prepare-academic-data.sh"

cat >"$FIXTURE_ROOT/mock-bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$DOCKER_COMMAND_LOG"
EOF
chmod 755 "$FIXTURE_ROOT/mock-bin/docker"

(
    cd "$FIXTURE_ROOT"
    PATH="$FIXTURE_ROOT/mock-bin:$PATH" \
    DOCKER_COMMAND_LOG="$FIXTURE_ROOT/docker.log" \
        ./load-db.sh >/dev/null
)

grep -qx 'prepared' "$FIXTURE_ROOT/prepare.marker"
grep -qx 'compose version' "$FIXTURE_ROOT/docker.log"
grep -qx 'compose up --detach --wait --wait-timeout 180 db' \
    "$FIXTURE_ROOT/docker.log"
grep -qx 'compose run --rm migrate' "$FIXTURE_ROOT/docker.log"
grep -qx 'compose run --rm --no-deps ingest' "$FIXTURE_ROOT/docker.log"
grep -qx 'compose ps db' "$FIXTURE_ROOT/docker.log"

if grep -Eq '(^|[[:space:]])api([[:space:]]|$)|build' "$FIXTURE_ROOT/docker.log"; then
    echo "DB 전용 적재 과정에서 API 빌드 또는 실행을 요청했습니다." >&2
    exit 1
fi

echo "load-db: PASS"
