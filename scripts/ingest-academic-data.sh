#!/bin/sh
set -eu

DATA_DIR=${DATA_DIR:-/workspace/data/database}
EXPECTED_COUNTS=${EXPECTED_COUNTS:-$DATA_DIR/expected-row-counts.tsv}
NORMALIZATION_SQL=${NORMALIZATION_SQL:-/workspace/normalization/normalize_academic_units.sql}
REFERENCE_PACKAGE_ID=academic-reference-2026-07-18-v1
REFERENCE_ARCHIVE_SHA256=78a388b2e8a2b014c0ea0fc38788428520019051c8de71a2d3004e3e45a57de8
CURRENT_CATALOG_CHECKSUM=bd78610cf34ac3c87111fa7d90e6ce26e6df83794e7b1e631a0c8fbc2b4deb81

for command_name in psql gzip sha256sum; do
    command -v "$command_name" >/dev/null 2>&1 || {
        echo "missing required command: $command_name" >&2
        exit 1
    }
done

if [ ! -f "$NORMALIZATION_SQL" ]; then
    echo "missing academic-unit normalization SQL: $NORMALIZATION_SQL" >&2
    exit 1
fi

cd "$DATA_DIR"

for required_file in \
    SHA256SUMS \
    expected-row-counts.tsv \
    current-catalog.sql.gz \
    reference-data.sql.gz.part-00 \
    reference-data.sql.gz.part-01 \
    reference-data.sql.gz.part-02; do
    if [ ! -f "$required_file" ]; then
        echo "missing academic data file: $DATA_DIR/$required_file" >&2
        echo "The SQL payload is not distributed through the public Git repository." >&2
        exit 1
    fi
done

sha256sum -c SHA256SUMS
reference_archive_sha256=$(cat reference-data.sql.gz.part-* | sha256sum | awk '{print $1}')
if [ "$reference_archive_sha256" != "$REFERENCE_ARCHIVE_SHA256" ]; then
    echo "reconstructed reference archive checksum mismatch" >&2
    exit 1
fi

reference_loaded=$(psql -X -q -v ON_ERROR_STOP=1 -Atc \
    "SELECT EXISTS (SELECT 1 FROM reference_data_imports WHERE package_id = '$REFERENCE_PACKAGE_ID')")
if [ "$reference_loaded" = "t" ]; then
    echo "Reference package already loaded: $REFERENCE_PACKAGE_ID"
else
    echo "Loading normalized historical and graduation-requirement data..."
    cat reference-data.sql.gz.part-* | gzip -dc | psql -X -q -v ON_ERROR_STOP=1
fi

catalog_loaded=$(psql -X -q -v ON_ERROR_STOP=1 -Atc \
    "SELECT EXISTS (SELECT 1 FROM data_imports WHERE checksum = '$CURRENT_CATALOG_CHECKSUM')")
if [ "$catalog_loaded" = "t" ]; then
    echo "Current catalog already loaded: $CURRENT_CATALOG_CHECKSUM"
else
    echo "Loading the current normalized course catalog..."
    gzip -dc current-catalog.sql.gz | psql -X -q -v ON_ERROR_STOP=1
fi

echo "Normalizing colleges, academic units, aliases, and section mappings..."
psql -X -q -v ON_ERROR_STOP=1 --single-transaction -f "$NORMALIZATION_SQL"

EXPECTED_COUNTS="$EXPECTED_COUNTS" /workspace/scripts/verify-database.sh
