WITH raw_notes AS (
    SELECT
        source_checksum,
        course_code,
        section_code,
        page_number,
        row_number,
        id,
        nullif(
            regexp_replace(
                coalesce(raw_cells ->> '교과번호', ''),
                '[[:space:]]+',
                '',
                'g'
            ),
            ''
        ) AS raw_course_code,
        nullif(
            btrim(
                regexp_replace(
                    regexp_replace(
                        coalesce(raw_cells ->> '비고', ''),
                        E'[ \t\f\v]*(\r\n|\r|\n)+[ \t\f\v]*',
                        '',
                        'g'
                    ),
                    E'[ \t\f\v]+',
                    ' ',
                    'g'
                )
            ),
            ''
        ) AS normalized_note
    FROM catalog_source_rows
    WHERE semester_id = '2026-2'
      AND source_checksum =
          '7c5f280277b63c302d02546a425afa95ea062a420afe178e7cede0af1551283c'
), course_groups AS (
    SELECT
        raw_notes.*,
        count(*) FILTER (WHERE raw_course_code IS NOT NULL) OVER (
            PARTITION BY source_checksum
            ORDER BY page_number, row_number, id
        ) AS course_group
    FROM raw_notes
), inherited_notes AS (
    SELECT
        course_groups.*,
        array_agg(normalized_note) FILTER (WHERE normalized_note IS NOT NULL) OVER (
            PARTITION BY source_checksum, course_group
            ORDER BY page_number, row_number, id
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
        ) AS notes_so_far
    FROM course_groups
), canonical_notes AS (
    SELECT DISTINCT ON (course_code, section_code)
        course_code,
        section_code,
        notes_so_far[array_length(notes_so_far, 1)] AS notes
    FROM inherited_notes
    ORDER BY course_code, section_code, page_number, row_number, id
)
UPDATE sections section
SET notes = canonical.notes,
    source_snapshot = jsonb_set(
        section.source_snapshot,
        '{notes}',
        coalesce(to_jsonb(canonical.notes), 'null'::jsonb),
        true
    )
FROM canonical_notes canonical
WHERE section.semester_id = '2026-2'
  AND section.course_code = canonical.course_code
  AND section.section_code = canonical.section_code;

UPDATE semesters
SET dataset_version = 'official-pdf-v2-7c5f280277b6'
WHERE id = '2026-2'
  AND source_checksum =
      '7c5f280277b63c302d02546a425afa95ea062a420afe178e7cede0af1551283c';

UPDATE catalog_sources
SET parser_version = 'official-catalog-pdf-v2',
    metadata = jsonb_set(
        jsonb_set(
            metadata,
            '{parserVersion}',
            '"official-catalog-pdf-v2"'::jsonb
        ),
        '{datasetVersion}',
        '"official-pdf-v2-7c5f280277b6"'::jsonb
    )
WHERE semester_id = '2026-2'
  AND checksum =
      '7c5f280277b63c302d02546a425afa95ea062a420afe178e7cede0af1551283c';

UPDATE data_imports import_record
SET parser_version = 'official-catalog-pdf-v2'
WHERE semester_id = '2026-2'
  AND checksum =
      '7c5f280277b63c302d02546a425afa95ea062a420afe178e7cede0af1551283c'
  AND parser_version = 'official-catalog-pdf-v1'
  AND NOT EXISTS (
      SELECT 1
      FROM data_imports existing
      WHERE existing.semester_id = import_record.semester_id
        AND existing.checksum = import_record.checksum
        AND existing.parser_version = 'official-catalog-pdf-v2'
  );

UPDATE data_imports
SET report = jsonb_set(
        jsonb_set(
            report,
            '{parserVersion}',
            '"official-catalog-pdf-v2"'::jsonb
        ),
        '{datasetVersion}',
        '"official-pdf-v2-7c5f280277b6"'::jsonb
    )
WHERE semester_id = '2026-2'
  AND checksum =
      '7c5f280277b63c302d02546a425afa95ea062a420afe178e7cede0af1551283c'
  AND parser_version = 'official-catalog-pdf-v2';
