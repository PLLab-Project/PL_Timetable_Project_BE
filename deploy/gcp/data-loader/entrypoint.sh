#!/bin/sh
set -eu
umask 077

: "${ACADEMIC_DATA_BUCKET:?ACADEMIC_DATA_BUCKET is required}"
: "${ACADEMIC_DATA_OBJECT:=academic-data-bundle.tar.gz}"
: "${PGHOST:?PGHOST is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"

metadata_url="http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"
token_response=$(curl --fail --silent --show-error \
    --header "Metadata-Flavor: Google" \
    "$metadata_url")
access_token=$(printf '%s' "$token_response" | jq -er '.access_token')

encoded_object=$(printf '%s' "$ACADEMIC_DATA_OBJECT" | jq -sRr @uri)
download_url="https://storage.googleapis.com/storage/v1/b/${ACADEMIC_DATA_BUCKET}/o/${encoded_object}?alt=media"

runtime_root=$(mktemp -d)
trap 'rm -rf "$runtime_root"' EXIT
data_directory="$runtime_root/data/database"
mkdir -p "$data_directory"

curl --fail --location --silent --show-error \
    --retry 5 --retry-delay 2 \
    --header "Authorization: Bearer $access_token" \
    "$download_url" \
    --output "$runtime_root/academic-data-bundle.tar.gz"
unset access_token token_response

tar -xzf "$runtime_root/academic-data-bundle.tar.gz" -C "$data_directory"
cp /workspace/static/SHA256SUMS /workspace/static/expected-row-counts.tsv "$data_directory/"

export DATA_DIR="$data_directory"
export EXPECTED_COUNTS="$data_directory/expected-row-counts.tsv"
export NORMALIZATION_SQL=/workspace/normalization/normalize_academic_units.sql
export VERIFY_SCRIPT=/workspace/scripts/verify-database.sh

exec /workspace/scripts/ingest-academic-data.sh
