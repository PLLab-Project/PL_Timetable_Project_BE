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

normalization_issue_count=$(psql -X -q -v ON_ERROR_STOP=1 -Atc "
    WITH latest_year AS (
        SELECT max(academic_year) AS value
        FROM historical_curriculum_departments
    ),
    expected_colleges AS (
        SELECT DISTINCT college_code
        FROM historical_curriculum_departments
        WHERE college_code IS NOT NULL
          AND college_name IS NOT NULL
    ),
    expected_units AS (
        SELECT DISTINCT department_code
        FROM historical_curriculum_departments
    ),
    expected_requirement_keys AS (
        SELECT academic_unit_key FROM curriculum_program_requirements
        UNION
        SELECT academic_unit_key FROM graduation_credit_profiles
        UNION
        SELECT academic_unit_key FROM graduation_assessment_profiles
        UNION
        SELECT academic_unit_key FROM graduation_legacy_requirements
        UNION
        SELECT academic_unit_key
        FROM graduation_requirement_rules
        WHERE academic_unit_key IS NOT NULL
    ),
    explicit_section_units AS (
        SELECT DISTINCT
            s.semester_id,
            s.course_code,
            s.section_code,
            context ->> 'departmentCode' AS academic_unit_code
        FROM sections s
        JOIN historical_course_offerings h
          ON h.academic_year::text = split_part(s.semester_id, '-', 1)
         AND h.term_code = split_part(s.semester_id, '-', 2)
         AND h.course_code = s.course_code
         AND h.section_code = s.section_code
        CROSS JOIN LATERAL json_array_elements(h.department_contexts) AS context
        WHERE context ->> 'departmentCode' IS NOT NULL
    )
    SELECT
        (SELECT count(*) FROM expected_colleges e
          LEFT JOIN academic_colleges c ON c.code = e.college_code
         WHERE c.code IS NULL)
      + (SELECT count(*) FROM expected_units e
          LEFT JOIN academic_units u ON u.code = e.department_code
         WHERE u.code IS NULL)
      + (SELECT count(*) FROM expected_requirement_keys e
          LEFT JOIN academic_units u ON u.normalized_key = e.academic_unit_key
         WHERE u.code IS NULL)
      + (SELECT count(*) FROM academic_units
         WHERE normalized_key <> normalize_academic_unit_key(name))
      + (SELECT count(*) FROM academic_units u
          CROSS JOIN latest_year y
         WHERE u.is_current <> (
             u.code_source = 'OFFICIAL_CURRICULUM'
             AND u.last_seen_year = y.value
         ))
      + (SELECT count(*) FROM curriculum_program_requirements
         WHERE academic_unit_code IS NULL)
      + (SELECT count(*) FROM graduation_credit_profiles
         WHERE academic_unit_code IS NULL)
      + (SELECT count(*) FROM graduation_assessment_profiles
         WHERE academic_unit_code IS NULL)
      + (SELECT count(*) FROM graduation_legacy_requirements
         WHERE academic_unit_code IS NULL)
      + (SELECT count(*) FROM graduation_requirement_rules
         WHERE academic_unit_key IS NOT NULL
           AND academic_unit_code IS NULL)
      + (SELECT count(*) FROM explicit_section_units e
          LEFT JOIN academic_units u ON u.code = e.academic_unit_code
          LEFT JOIN section_academic_units m
            ON m.semester_id = e.semester_id
           AND m.course_code = e.course_code
           AND m.section_code = e.section_code
           AND m.academic_unit_code = e.academic_unit_code
           AND m.relation_type = 'OFFERING'
         WHERE u.code IS NULL OR m.academic_unit_code IS NULL);
")

if [ "$normalization_issue_count" != "0" ]; then
    echo "academic-unit normalization check failed: $normalization_issue_count issues" >&2
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
