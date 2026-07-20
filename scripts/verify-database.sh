#!/bin/sh
set -eu

EXPECTED_COUNTS=${EXPECTED_COUNTS:-/workspace/data/database/expected-row-counts.tsv}
failure=0

printf '%-56s %12s %12s\n' table expected actual
while IFS="$(printf '\t')" read -r table expected; do
    case "$table" in
        ''|'#'*) continue ;;
    esac

    actual=$(psql -X -q -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM public.$table")
    printf '%-56s %12s %12s\n' "$table" "$expected" "$actual"
    if [ "$actual" != "$expected" ]; then
        failure=1
    fi
done < "$EXPECTED_COUNTS"

orphan_count=$(psql -X -q -v ON_ERROR_STOP=1 -Atc "
    SELECT
        (SELECT count(*) FROM curriculum_required_courses c
          LEFT JOIN curriculum_program_requirements p ON p.id = c.program_id
         WHERE p.id IS NULL)
      + (SELECT count(*) FROM historical_course_offerings o
          LEFT JOIN historical_term_datasets d ON d.id = o.dataset_id
         WHERE d.id IS NULL)
      + (SELECT count(*) FROM graduation_credit_profiles p
          LEFT JOIN requirement_datasets d ON d.id = p.dataset_id
         WHERE d.id IS NULL);
")

if [ "$orphan_count" != "0" ]; then
    echo "foreign-key integrity check failed: $orphan_count orphan rows" >&2
    failure=1
fi

flyway_failures=$(psql -X -q -v ON_ERROR_STOP=1 -Atc \
    "SELECT count(*) FROM flyway_schema_history WHERE success IS NOT TRUE")
if [ "$flyway_failures" != "0" ]; then
    echo "Flyway history contains $flyway_failures failed migrations" >&2
    failure=1
fi

if [ "$failure" -ne 0 ]; then
    echo "database verification failed" >&2
    exit 1
fi

echo "database verification passed"
