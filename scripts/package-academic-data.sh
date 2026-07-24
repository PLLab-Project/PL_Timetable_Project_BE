#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

data_directory="data/database"
output="${1:-${data_directory}/academic-data-bundle.tar.gz}"
payload_files=(
    current-catalog.sql.gz
    reference-data.sql.gz.part-00
    reference-data.sql.gz.part-01
    reference-data.sql.gz.part-02
)

for payload in "${payload_files[@]}"; do
    [[ -f "${data_directory}/${payload}" ]] || {
        echo "번들에 넣을 파일이 없습니다: ${data_directory}/${payload}" >&2
        exit 1
    }
done

(
    cd "$data_directory"
    sha256sum --check SHA256SUMS
)

mkdir -p "$(dirname "$output")"
temporary="${output}.tmp"
trap 'rm -f "$temporary"' EXIT

tar -czf "$temporary" -C "$data_directory" "${payload_files[@]}"
mv "$temporary" "$output"
trap - EXIT

echo "단일 학사 데이터 번들 생성 완료: $output"
du -h "$output"
sha256sum "$output"
