#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
data_directory="${DATA_DIR:-${ROOT}/data/database}"
bundle="${ACADEMIC_DATA_BUNDLE:-${data_directory}/academic-data-bundle.tar.gz}"
checksums="${ACADEMIC_DATA_CHECKSUMS:-${data_directory}/SHA256SUMS}"
stamp="${data_directory}/.academic-data-bundle.sha256"

payload_files=(
    current-catalog.sql.gz
    reference-data.sql.gz.part-00
    reference-data.sql.gz.part-01
    reference-data.sql.gz.part-02
)

for command_name in tar sha256sum sed sort mktemp mv cut tr rm; do
    command -v "$command_name" >/dev/null 2>&1 || {
        echo "필수 명령을 찾을 수 없습니다: $command_name" >&2
        exit 1
    }
done

[[ -f "$checksums" ]] || {
    echo "학사 데이터 체크섬 파일이 없습니다: $checksums" >&2
    exit 1
}
mkdir -p "$data_directory"

payloads_complete=true
for payload in "${payload_files[@]}"; do
    [[ -f "${data_directory}/${payload}" ]] || payloads_complete=false
done

payloads_valid=false
if [[ "$payloads_complete" == true ]] \
    && (
        cd "$data_directory"
        sha256sum --check "$checksums" >/dev/null 2>&1
    ); then
    payloads_valid=true
fi

bundle_checksum=""
recorded_checksum=""
if [[ -f "$bundle" ]]; then
    bundle_checksum="$(sha256sum "$bundle" | cut -d' ' -f1)"
fi
if [[ -f "$stamp" ]]; then
    recorded_checksum="$(tr -d '[:space:]' <"$stamp")"
fi

needs_extraction=false
if [[ "$payloads_valid" != true ]]; then
    needs_extraction=true
elif [[ -n "$bundle_checksum" && "$bundle_checksum" != "$recorded_checksum" ]]; then
    needs_extraction=true
fi

if [[ "$needs_extraction" != true ]]; then
    echo "검증된 학사 데이터 파일을 그대로 사용합니다."
    exit 0
fi

[[ -f "$bundle" ]] || {
    echo "학사 데이터 번들이 없습니다: $bundle" >&2
    echo "담당자에게 전달받은 단일 번들을 data/database/에 배치하세요." >&2
    exit 1
}

expected_entries="$(printf '%s\n' "${payload_files[@]}" | LC_ALL=C sort)"
actual_entries="$(
    tar -tzf "$bundle" \
        | sed 's#^\./##' \
        | sed '/\/$/d' \
        | LC_ALL=C sort
)"
if [[ "$actual_entries" != "$expected_entries" ]]; then
    echo "학사 데이터 번들의 파일 구성이 올바르지 않습니다." >&2
    exit 1
fi

staging_directory="$(mktemp -d "${data_directory}/.academic-data.XXXXXX")"
trap 'rm -rf "$staging_directory"' EXIT

tar -xzf "$bundle" \
    -C "$staging_directory" \
    --no-same-owner \
    --no-same-permissions \
    "${payload_files[@]}"

(
    cd "$staging_directory"
    sha256sum --check "$checksums"
)

for payload in "${payload_files[@]}"; do
    mv -f "${staging_directory}/${payload}" "${data_directory}/${payload}"
done
printf '%s\n' "$bundle_checksum" >"${stamp}.tmp"
mv -f "${stamp}.tmp" "$stamp"

rm -rf "$staging_directory"
trap - EXIT
echo "학사 데이터 번들을 안전하게 검증하고 준비했습니다."
