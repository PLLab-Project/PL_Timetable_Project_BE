#!/usr/bin/env bash
set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET_SCRIPT="${SOURCE_ROOT}/scripts/prepare-academic-data.sh"
FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

[[ -x "$TARGET_SCRIPT" ]] || {
    echo "데이터 준비 스크립트를 실행할 수 없습니다: $TARGET_SCRIPT" >&2
    exit 1
}

payload_files=(
    current-catalog.sql.gz
    reference-data.sql.gz.part-00
    reference-data.sql.gz.part-01
    reference-data.sql.gz.part-02
)

make_bundle() {
    local version="$1"
    local source_directory="$FIXTURE_ROOT/source"
    rm -rf "$source_directory"
    mkdir -p "$source_directory"
    for payload in "${payload_files[@]}"; do
        printf '%s:%s\n' "$version" "$payload" >"$source_directory/$payload"
    done
    (
        cd "$source_directory"
        sha256sum "${payload_files[@]}" >"$FIXTURE_ROOT/SHA256SUMS"
        tar -czf "$FIXTURE_ROOT/academic-data-bundle.tar.gz" "${payload_files[@]}"
    )
}

mkdir -p "$FIXTURE_ROOT/data"
make_bundle v1

DATA_DIR="$FIXTURE_ROOT/data" \
ACADEMIC_DATA_BUNDLE="$FIXTURE_ROOT/academic-data-bundle.tar.gz" \
ACADEMIC_DATA_CHECKSUMS="$FIXTURE_ROOT/SHA256SUMS" \
    "$TARGET_SCRIPT" >/dev/null

grep -q '^v1:' "$FIXTURE_ROOT/data/current-catalog.sql.gz"
[[ -f "$FIXTURE_ROOT/data/.academic-data-bundle.sha256" ]]

printf 'corrupted\n' >"$FIXTURE_ROOT/data/current-catalog.sql.gz"
DATA_DIR="$FIXTURE_ROOT/data" \
ACADEMIC_DATA_BUNDLE="$FIXTURE_ROOT/academic-data-bundle.tar.gz" \
ACADEMIC_DATA_CHECKSUMS="$FIXTURE_ROOT/SHA256SUMS" \
    "$TARGET_SCRIPT" >/dev/null
grep -q '^v1:' "$FIXTURE_ROOT/data/current-catalog.sql.gz"

make_bundle v2
DATA_DIR="$FIXTURE_ROOT/data" \
ACADEMIC_DATA_BUNDLE="$FIXTURE_ROOT/academic-data-bundle.tar.gz" \
ACADEMIC_DATA_CHECKSUMS="$FIXTURE_ROOT/SHA256SUMS" \
    "$TARGET_SCRIPT" >/dev/null
grep -q '^v2:' "$FIXTURE_ROOT/data/current-catalog.sql.gz"

printf 'unexpected\n' >"$FIXTURE_ROOT/source/unexpected.txt"
(
    cd "$FIXTURE_ROOT/source"
    tar -czf "$FIXTURE_ROOT/academic-data-bundle.tar.gz" \
        "${payload_files[@]}" unexpected.txt
)
if DATA_DIR="$FIXTURE_ROOT/data" \
    ACADEMIC_DATA_BUNDLE="$FIXTURE_ROOT/academic-data-bundle.tar.gz" \
    ACADEMIC_DATA_CHECKSUMS="$FIXTURE_ROOT/SHA256SUMS" \
        "$TARGET_SCRIPT" >/dev/null 2>&1; then
    echo "허용하지 않은 파일이 든 번들을 거부해야 합니다." >&2
    exit 1
fi
grep -q '^v2:' "$FIXTURE_ROOT/data/current-catalog.sql.gz"

echo "prepare-academic-data: PASS"
