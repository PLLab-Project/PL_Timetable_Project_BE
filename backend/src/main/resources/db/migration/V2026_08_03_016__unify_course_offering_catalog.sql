ALTER TABLE sections
    ADD COLUMN offering_id varchar(104)
        GENERATED ALWAYS AS (
            semester_id || ':' || course_code || ':' || section_code
        ) STORED,
    ADD COLUMN source_type varchar(32) NOT NULL DEFAULT 'OFFICIAL_CATALOG',
    ADD COLUMN historical_offering_id varchar(36),
    ADD COLUMN offering_category varchar(160),
    ADD COLUMN offering_credits numeric(5, 2),
    ADD COLUMN offering_lecture_hours numeric(5, 2),
    ADD COLUMN offering_practice_hours numeric(5, 2);

ALTER TABLE sections
    ADD CONSTRAINT uq_sections_offering_id UNIQUE (offering_id),
    ADD CONSTRAINT uq_sections_historical_offering_id
        UNIQUE (historical_offering_id),
    ADD CONSTRAINT fk_sections_historical_offering
        FOREIGN KEY (historical_offering_id)
        REFERENCES historical_course_offerings(id)
        ON DELETE SET NULL,
    ADD CONSTRAINT ck_sections_source_type
        CHECK (source_type IN ('OFFICIAL_CATALOG', 'HISTORICAL_ARCHIVE')),
    ADD CONSTRAINT ck_sections_offering_credits
        CHECK (offering_credits IS NULL OR offering_credits >= 0),
    ADD CONSTRAINT ck_sections_offering_lecture_hours
        CHECK (offering_lecture_hours IS NULL OR offering_lecture_hours >= 0),
    ADD CONSTRAINT ck_sections_offering_practice_hours
        CHECK (offering_practice_hours IS NULL OR offering_practice_hours >= 0);

CREATE INDEX ix_sections_source_type_semester
    ON sections (source_type, semester_id);

COMMENT ON COLUMN sections.offering_id IS
    'Stable public identifier for one semester/course/section offering.';
COMMENT ON COLUMN sections.source_type IS
    'Authoritative normalized source: official catalog or historical archive.';
COMMENT ON COLUMN sections.historical_offering_id IS
    'Optional provenance link to the immutable historical source record.';
COMMENT ON COLUMN sections.offering_category IS
    'Offering-specific category override; historical values can differ by section.';
COMMENT ON COLUMN sections.offering_credits IS
    'Offering-specific credit override; historical values can differ by section.';

CREATE OR REPLACE FUNCTION reconcile_historical_course_offerings()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO semesters (
        id,
        prepared_at,
        dataset_version,
        source_checksum,
        is_active,
        created_at
    )
    SELECT
        dataset.academic_year::text || '-' || dataset.term_code,
        dataset.collected_at::date,
        left(
            'historical-' || dataset.schema_version || '-'
                || left(dataset.source_checksum, 12),
            64
        ),
        dataset.source_checksum,
        false,
        dataset.imported_at
    FROM historical_term_datasets dataset
    ON CONFLICT (id) DO NOTHING;

    WITH course_variants AS (
        SELECT
            offering.academic_year::text || '-' || offering.term_code
                AS semester_id,
            offering.course_code,
            offering.korean_name,
            offering.completion_category,
            offering.credits,
            offering.lecture_hours,
            offering.practice_hours,
            count(*) AS occurrence_count,
            min(offering.id) AS first_offering_id
        FROM historical_course_offerings offering
        GROUP BY
            offering.academic_year,
            offering.term_code,
            offering.course_code,
            offering.korean_name,
            offering.completion_category,
            offering.credits,
            offering.lecture_hours,
            offering.practice_hours
    ),
    representative_courses AS (
        SELECT
            variant.*,
            row_number() OVER (
                PARTITION BY variant.semester_id, variant.course_code
                ORDER BY
                    variant.occurrence_count DESC,
                    variant.first_offering_id
            ) AS priority
        FROM course_variants variant
    )
    INSERT INTO courses (
        semester_id,
        course_code,
        name,
        category,
        credits,
        lecture_hours,
        practice_hours
    )
    SELECT
        representative.semester_id,
        representative.course_code,
        representative.korean_name,
        coalesce(representative.completion_category, '미분류'),
        coalesce(representative.credits, 0)::numeric(5, 2),
        representative.lecture_hours::numeric(5, 2),
        representative.practice_hours::numeric(5, 2)
    FROM representative_courses representative
    WHERE representative.priority = 1
    ON CONFLICT (semester_id, course_code) DO NOTHING;

    UPDATE sections section
    SET historical_offering_id = offering.id
    FROM historical_course_offerings offering
    WHERE section.semester_id =
              offering.academic_year::text || '-' || offering.term_code
      AND section.course_code = offering.course_code
      AND section.section_code = offering.section_code
      AND section.historical_offering_id IS DISTINCT FROM offering.id;

    INSERT INTO sections (
        semester_id,
        course_code,
        section_code,
        professor,
        raw_lecture_time,
        time_to_be_announced,
        warning_codes,
        target_grade,
        raw_location,
        source_snapshot,
        source_type,
        historical_offering_id,
        offering_category,
        offering_credits,
        offering_lecture_hours,
        offering_practice_hours
    )
    SELECT
        offering.academic_year::text || '-' || offering.term_code,
        offering.course_code,
        offering.section_code,
        offering.professor_name,
        coalesce(offering.raw_lecture_time, ''),
        NOT (
            coalesce(offering.raw_lecture_time, '')
                ~ '([월화수목금토일])\s*(\d{1,2}):(\d{2})\s*[-~]\s*(\d{1,2}):(\d{2})'
        ),
        CASE
            WHEN coalesce(offering.raw_lecture_time, '') <> ''
             AND NOT (
                offering.raw_lecture_time
                    ~ '([월화수목금토일])\s*(\d{1,2}):(\d{2})\s*[-~]\s*(\d{1,2}):(\d{2})'
             )
                THEN '["HISTORICAL_TIME_UNPARSED"]'::jsonb
            ELSE '[]'::jsonb
        END,
        offering.target_grade,
        offering.raw_location,
        jsonb_build_object('historicalOfferingId', offering.id),
        'HISTORICAL_ARCHIVE',
        offering.id,
        offering.completion_category,
        offering.credits::numeric(5, 2),
        offering.lecture_hours::numeric(5, 2),
        offering.practice_hours::numeric(5, 2)
    FROM historical_course_offerings offering
    ON CONFLICT (semester_id, course_code, section_code) DO NOTHING;

    INSERT INTO rooms (
        semester_id,
        code,
        building_code,
        building_name,
        label,
        room_type,
        capacity
    )
    SELECT DISTINCT
        section.semester_id,
        'HIST-' || left(md5(section.raw_location), 32),
        NULL::varchar(40),
        NULL::text,
        section.raw_location,
        'HISTORICAL_RAW',
        NULL::integer
    FROM sections section
    WHERE section.source_type = 'HISTORICAL_ARCHIVE'
      AND nullif(btrim(section.raw_location), '') IS NOT NULL
    ON CONFLICT (semester_id, code) DO NOTHING;

    WITH parsed_sessions AS (
        SELECT
            section.semester_id,
            section.course_code,
            section.section_code,
            matched.parts[1] AS day,
            (matched.parts[2]::integer * 60 + matched.parts[3]::integer)
                AS start_minute,
            (matched.parts[4]::integer * 60 + matched.parts[5]::integer)
                AS end_minute,
            CASE
                WHEN nullif(btrim(section.raw_location), '') IS NULL THEN NULL
                ELSE 'HIST-' || left(md5(section.raw_location), 32)
            END AS room_code,
            matched.ordinality::smallint AS sequence_no
        FROM sections section
        CROSS JOIN LATERAL regexp_matches(
            section.raw_lecture_time,
            '([월화수목금토일])\s*(\d{1,2}):(\d{2})\s*[-~]\s*(\d{1,2}):(\d{2})',
            'g'
        ) WITH ORDINALITY AS matched(parts, ordinality)
        WHERE section.source_type = 'HISTORICAL_ARCHIVE'
    )
    INSERT INTO sessions (
        semester_id,
        course_code,
        section_code,
        day,
        start_minute,
        end_minute,
        room_code,
        sequence_no
    )
    SELECT
        parsed.semester_id,
        parsed.course_code,
        parsed.section_code,
        parsed.day,
        parsed.start_minute,
        parsed.end_minute,
        parsed.room_code,
        parsed.sequence_no
    FROM parsed_sessions parsed
    WHERE parsed.start_minute >= 0
      AND parsed.start_minute < 1440
      AND parsed.end_minute > parsed.start_minute
      AND parsed.end_minute <= 1440
    ON CONFLICT (semester_id, course_code, section_code, sequence_no)
        DO NOTHING;
END;
$$;

COMMENT ON FUNCTION reconcile_historical_course_offerings() IS
    'Idempotently projects immutable historical source records into the canonical multi-semester catalog.';

SELECT reconcile_historical_course_offerings();

ALTER TABLE completed_courses
    ADD COLUMN offering_id varchar(104);

UPDATE completed_courses completed
SET offering_id = section.offering_id
FROM sections section
WHERE completed.historical_offering_id = section.historical_offering_id;

ALTER TABLE completed_courses
    ADD CONSTRAINT fk_completed_courses_offering
        FOREIGN KEY (offering_id)
        REFERENCES sections(offering_id)
        ON DELETE SET NULL;

CREATE INDEX ix_completed_courses_offering_id
    ON completed_courses (offering_id);

ALTER TABLE completed_courses
    DROP CONSTRAINT ck_completed_courses_input_source,
    ADD CONSTRAINT ck_completed_courses_input_source CHECK (
        input_source IN ('MANUAL', 'CATALOG', 'TIMETABLE', 'OCR', 'IMPORT')
    );

COMMENT ON COLUMN completed_courses.offering_id IS
    'Canonical section offering selected by catalog search, OCR, or timetable import.';
