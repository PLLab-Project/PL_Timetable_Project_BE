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

official_catalog_issue_count=$(psql -X -q -v ON_ERROR_STOP=1 -Atc "
    SELECT
        (SELECT count(*)
           FROM catalog_sources source
          WHERE source.raw_row_count <> (
                    SELECT count(*)
                      FROM catalog_source_rows row
                     WHERE row.source_checksum = source.checksum
                )
             OR source.supplemental_row_count <> (
                    SELECT count(*)
                      FROM catalog_program_course_listings listing
                     WHERE listing.source_checksum = source.checksum
                )
             OR source.unique_section_count <> (
                    SELECT count(*)
                      FROM sections section
                     WHERE section.semester_id = source.semester_id
                       AND section.source_type = 'OFFICIAL_CATALOG'
                ))
      + (SELECT count(*)
           FROM catalog_sources source
           LEFT JOIN data_imports import
             ON import.semester_id = source.semester_id
            AND import.checksum = source.checksum
          WHERE import.id IS NULL)
      + (SELECT count(*)
           FROM sections section
           JOIN catalog_sources source
             ON source.semester_id = section.semester_id
          WHERE section.source_type = 'OFFICIAL_CATALOG'
            AND NOT EXISTS (
                    SELECT 1
                      FROM catalog_source_rows row
                     WHERE row.source_checksum = source.checksum
                       AND row.semester_id = section.semester_id
                       AND row.course_code = section.course_code
                       AND row.section_code = section.section_code
                ))
      + (SELECT count(*)
           FROM catalog_source_rows row
          WHERE NOT EXISTS (
                    SELECT 1
                      FROM section_classification_contexts context
                     WHERE context.source_checksum = row.source_checksum
                       AND context.semester_id = row.semester_id
                       AND context.course_code = row.course_code
                       AND context.section_code = row.section_code
                       AND context.source_page = row.page_number
                       AND context.source_row = row.row_number
                ))
      + (SELECT count(*)
           FROM catalog_sources source
          WHERE (source.metadata ->> 'microMajorResolvedRows')::integer <> (
                    SELECT count(*)
                      FROM catalog_program_course_listings listing
                     WHERE listing.source_checksum = source.checksum
                       AND listing.resolution_status = 'RESOLVED'
                )
             OR (source.metadata ->> 'microMajorUnopenedRows')::integer <> (
                    SELECT count(*)
                      FROM catalog_program_course_listings listing
                     WHERE listing.source_checksum = source.checksum
                       AND listing.resolution_status = 'NOT_OFFERED'
                )
             OR (source.metadata ->> 'microMajorSourceNotFoundRows')::integer <> (
                    SELECT count(*)
                      FROM catalog_program_course_listings listing
                     WHERE listing.source_checksum = source.checksum
                       AND listing.resolution_status = 'SOURCE_NOT_FOUND'
                )
             OR (source.metadata ->> 'microMajorAmbiguousRows')::integer <> (
                    SELECT count(*)
                      FROM catalog_program_course_listings listing
                     WHERE listing.source_checksum = source.checksum
                       AND listing.resolution_status = 'AMBIGUOUS'
                ))
      + (SELECT count(*)
           FROM section_classification_contexts context
          WHERE context.context_kind = 'ACADEMIC_UNIT'
            AND context.academic_unit_code IS NULL)
      + (SELECT count(*)
           FROM catalog_program_course_listings listing
          WHERE listing.offering_academic_unit_code IS NULL)
      + (SELECT count(*)
           FROM catalog_source_rows row
          WHERE row.raw_cells = '{}'::jsonb)
      + (SELECT count(*)
           FROM sessions session
          WHERE session.sequence_no IS NULL)
      + (SELECT count(*)
           FROM sessions session
           JOIN sections section
             ON section.semester_id = session.semester_id
            AND section.course_code = session.course_code
            AND section.section_code = session.section_code
          WHERE section.source_type = 'OFFICIAL_CATALOG'
            AND session.room_code IS NOT NULL
            AND NOT EXISTS (
                    SELECT 1
                      FROM session_rooms room
                     WHERE room.session_id = session.id
                       AND room.semester_id = session.semester_id
                       AND room.room_code = session.room_code
                ));
")

if [ "$official_catalog_issue_count" != "0" ]; then
    echo "official catalog preservation check failed: $official_catalog_issue_count issues" >&2
    failure=1
fi

canonical_offering_issue_count=$(psql -X -q -v ON_ERROR_STOP=1 -Atc "
    SELECT
        (SELECT count(*)
           FROM historical_course_offerings historical
           LEFT JOIN sections section
             ON section.historical_offering_id = historical.id
          WHERE section.offering_id IS NULL)
      + (SELECT count(*)
           FROM sections section
          WHERE section.source_type = 'HISTORICAL_ARCHIVE'
            AND section.historical_offering_id IS NULL)
      + (SELECT count(*)
           FROM sections section
          WHERE section.offering_id <>
                section.semester_id || ':' || section.course_code || ':' ||
                section.section_code)
      + (SELECT count(*)
           FROM sessions session
           LEFT JOIN sections section
             ON section.semester_id = session.semester_id
            AND section.course_code = session.course_code
            AND section.section_code = session.section_code
          WHERE section.offering_id IS NULL);
")

if [ "$canonical_offering_issue_count" != "0" ]; then
    echo "canonical course-offering check failed: $canonical_offering_issue_count issues" >&2
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
