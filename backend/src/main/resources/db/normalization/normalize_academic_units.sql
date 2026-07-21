WITH latest_year AS (
    SELECT max(academic_year) AS value
    FROM historical_curriculum_departments
),
college_stats AS (
    SELECT
        college_code AS code,
        min(academic_year) AS first_seen_year,
        max(academic_year) AS last_seen_year
    FROM historical_curriculum_departments
    WHERE college_code IS NOT NULL
      AND college_name IS NOT NULL
    GROUP BY college_code
),
college_latest AS (
    SELECT DISTINCT ON (college_code)
        college_code AS code,
        college_name AS name
    FROM historical_curriculum_departments
    WHERE college_code IS NOT NULL
      AND college_name IS NOT NULL
    ORDER BY college_code, academic_year DESC
)
INSERT INTO academic_colleges (
    code,
    name,
    first_seen_year,
    last_seen_year,
    is_current
)
SELECT
    stats.code,
    latest.name,
    stats.first_seen_year,
    stats.last_seen_year,
    stats.last_seen_year = years.value
FROM college_stats stats
JOIN college_latest latest USING (code)
CROSS JOIN latest_year years
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    first_seen_year = EXCLUDED.first_seen_year,
    last_seen_year = EXCLUDED.last_seen_year,
    is_current = EXCLUDED.is_current,
    updated_at = now();

WITH latest_year AS (
    SELECT max(academic_year) AS value
    FROM historical_curriculum_departments
),
unit_stats AS (
    SELECT
        department_code AS code,
        min(academic_year) AS first_seen_year,
        max(academic_year) AS last_seen_year
    FROM historical_curriculum_departments
    GROUP BY department_code
),
unit_latest AS (
    SELECT DISTINCT ON (department_code)
        department_code AS code,
        department_name AS name,
        college_code
    FROM historical_curriculum_departments
    ORDER BY department_code, academic_year DESC
)
INSERT INTO academic_units (
    code,
    college_code,
    name,
    code_source,
    first_seen_year,
    last_seen_year,
    is_current
)
SELECT
    stats.code,
    latest.college_code,
    latest.name,
    'OFFICIAL_CURRICULUM',
    stats.first_seen_year,
    stats.last_seen_year,
    stats.last_seen_year = years.value
FROM unit_stats stats
JOIN unit_latest latest USING (code)
CROSS JOIN latest_year years
ON CONFLICT (code) DO UPDATE SET
    college_code = EXCLUDED.college_code,
    name = EXCLUDED.name,
    code_source = EXCLUDED.code_source,
    first_seen_year = EXCLUDED.first_seen_year,
    last_seen_year = EXCLUDED.last_seen_year,
    is_current = EXCLUDED.is_current,
    updated_at = now();

WITH latest_curriculum_year AS (
    SELECT max(academic_year) AS value
    FROM historical_curriculum_departments
),
requirement_unit_ranges AS (
    SELECT
        academic_unit AS name,
        academic_unit_key AS normalized_key,
        admission_year AS first_seen_year,
        admission_year AS last_seen_year
    FROM curriculum_program_requirements

    UNION ALL

    SELECT academic_unit, academic_unit_key, admission_year, admission_year
    FROM graduation_credit_profiles

    UNION ALL

    SELECT academic_unit, academic_unit_key, effective_year, effective_year
    FROM graduation_assessment_profiles

    UNION ALL

    SELECT academic_unit, academic_unit_key, effective_year, effective_year
    FROM graduation_legacy_requirements

    UNION ALL

    SELECT
        academic_unit,
        academic_unit_key,
        COALESCE(admission_year_start, effective_year, years.value),
        COALESCE(admission_year_end, effective_year, years.value)
    FROM graduation_requirement_rules
    CROSS JOIN latest_curriculum_year years
    WHERE academic_unit IS NOT NULL
      AND academic_unit_key IS NOT NULL
),
requirement_unit_stats AS (
    SELECT
        normalized_key,
        min(first_seen_year) AS first_seen_year,
        max(last_seen_year) AS last_seen_year
    FROM requirement_unit_ranges
    GROUP BY normalized_key
),
requirement_unit_latest AS (
    SELECT DISTINCT ON (normalized_key)
        normalized_key,
        name
    FROM requirement_unit_ranges
    ORDER BY normalized_key, last_seen_year DESC, name
)
INSERT INTO academic_units (
    code,
    college_code,
    name,
    code_source,
    first_seen_year,
    last_seen_year,
    is_current
)
SELECT
    'REQ-' || md5(stats.normalized_key),
    NULL,
    latest.name,
    'REQUIREMENT_DERIVED',
    stats.first_seen_year,
    stats.last_seen_year,
    false
FROM requirement_unit_stats stats
JOIN requirement_unit_latest latest USING (normalized_key)
WHERE NOT EXISTS (
    SELECT 1
    FROM academic_units units
    WHERE units.normalized_key = stats.normalized_key
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    code_source = EXCLUDED.code_source,
    first_seen_year = EXCLUDED.first_seen_year,
    last_seen_year = EXCLUDED.last_seen_year,
    is_current = false,
    updated_at = now();

DELETE FROM academic_unit_aliases
WHERE source_kind IN (
    'HISTORICAL_CURRICULUM',
    'CURRICULUM_REQUIREMENT',
    'CURRICULUM_REQUIREMENT_ALIAS',
    'GRADUATION_CREDIT',
    'GRADUATION_CREDIT_ALIAS',
    'GRADUATION_ASSESSMENT',
    'GRADUATION_LEGACY',
    'GRADUATION_RULE'
);

INSERT INTO academic_unit_aliases (
    academic_unit_code,
    alias,
    valid_from_year,
    valid_to_year,
    source_kind,
    is_primary
)
SELECT
    department_code,
    department_name,
    min(academic_year),
    max(academic_year),
    'HISTORICAL_CURRICULUM',
    bool_or(units.name = department_name)
FROM historical_curriculum_departments history
JOIN academic_units units ON units.code = history.department_code
GROUP BY department_code, department_name;

WITH requirement_names AS (
    SELECT
        academic_unit AS alias,
        academic_unit_key AS alias_key,
        min(admission_year) AS valid_from_year,
        max(admission_year) AS valid_to_year,
        'CURRICULUM_REQUIREMENT'::varchar(40) AS source_kind
    FROM curriculum_program_requirements
    GROUP BY academic_unit, academic_unit_key

    UNION ALL

    SELECT
        academic_unit,
        academic_unit_key,
        min(admission_year),
        max(admission_year),
        'GRADUATION_CREDIT'::varchar(40)
    FROM graduation_credit_profiles
    GROUP BY academic_unit, academic_unit_key

    UNION ALL

    SELECT
        academic_unit,
        academic_unit_key,
        min(effective_year),
        max(effective_year),
        'GRADUATION_ASSESSMENT'::varchar(40)
    FROM graduation_assessment_profiles
    GROUP BY academic_unit, academic_unit_key

    UNION ALL

    SELECT
        academic_unit,
        academic_unit_key,
        min(effective_year),
        max(effective_year),
        'GRADUATION_LEGACY'::varchar(40)
    FROM graduation_legacy_requirements
    GROUP BY academic_unit, academic_unit_key

    UNION ALL

    SELECT
        academic_unit,
        academic_unit_key,
        min(COALESCE(admission_year_start, effective_year)),
        max(COALESCE(admission_year_end, effective_year)),
        'GRADUATION_RULE'::varchar(40)
    FROM graduation_requirement_rules
    WHERE academic_unit IS NOT NULL
      AND academic_unit_key IS NOT NULL
    GROUP BY academic_unit, academic_unit_key
),
resolved_requirement_names AS (
    SELECT DISTINCT ON (
        units.code,
        normalize_academic_unit_key(names.alias),
        names.valid_from_year,
        names.valid_to_year,
        names.source_kind
    )
        units.code AS academic_unit_code,
        names.alias,
        names.valid_from_year,
        names.valid_to_year,
        names.source_kind,
        units.normalized_key = names.alias_key AS is_primary
    FROM requirement_names names
    JOIN academic_units units ON units.normalized_key = names.alias_key
    ORDER BY
        units.code,
        normalize_academic_unit_key(names.alias),
        names.valid_from_year,
        names.valid_to_year,
        names.source_kind,
        (names.alias = units.name) DESC,
        names.alias
)
INSERT INTO academic_unit_aliases (
    academic_unit_code,
    alias,
    valid_from_year,
    valid_to_year,
    source_kind,
    is_primary
)
SELECT
    academic_unit_code,
    alias,
    valid_from_year,
    valid_to_year,
    source_kind,
    is_primary
FROM resolved_requirement_names
ON CONFLICT ON CONSTRAINT uq_academic_unit_alias_source DO UPDATE SET
    alias = EXCLUDED.alias,
    is_primary = EXCLUDED.is_primary;

WITH program_aliases AS (
    SELECT
        units.code AS academic_unit_code,
        min(aliases.alias) AS alias,
        min(programs.admission_year) AS valid_from_year,
        max(programs.admission_year) AS valid_to_year,
        bool_or(aliases.is_primary) AS is_primary
    FROM curriculum_program_aliases aliases
    JOIN curriculum_program_requirements programs
      ON programs.id = aliases.program_id
    JOIN academic_units units
      ON units.normalized_key = programs.academic_unit_key
    GROUP BY units.code, normalize_academic_unit_key(aliases.alias)
)
INSERT INTO academic_unit_aliases (
    academic_unit_code,
    alias,
    valid_from_year,
    valid_to_year,
    source_kind,
    is_primary
)
SELECT
    academic_unit_code,
    alias,
    valid_from_year,
    valid_to_year,
    'CURRICULUM_REQUIREMENT_ALIAS',
    is_primary
FROM program_aliases
ON CONFLICT ON CONSTRAINT uq_academic_unit_alias_source DO UPDATE SET
    alias = EXCLUDED.alias,
    is_primary = EXCLUDED.is_primary;

WITH credit_aliases AS (
    SELECT
        units.code AS academic_unit_code,
        min(aliases.alias) AS alias,
        min(profiles.admission_year) AS valid_from_year,
        max(profiles.admission_year) AS valid_to_year
    FROM graduation_credit_profile_academic_unit_aliases aliases
    JOIN graduation_credit_profiles profiles
      ON profiles.id = aliases.profile_id
    JOIN academic_units units
      ON units.normalized_key = profiles.academic_unit_key
    GROUP BY units.code, normalize_academic_unit_key(aliases.alias)
)
INSERT INTO academic_unit_aliases (
    academic_unit_code,
    alias,
    valid_from_year,
    valid_to_year,
    source_kind,
    is_primary
)
SELECT
    academic_unit_code,
    alias,
    valid_from_year,
    valid_to_year,
    'GRADUATION_CREDIT_ALIAS',
    false
FROM credit_aliases
ON CONFLICT ON CONSTRAINT uq_academic_unit_alias_source DO UPDATE SET
    alias = EXCLUDED.alias;

UPDATE curriculum_program_requirements requirements
SET academic_unit_code = units.code
FROM academic_units units
WHERE units.normalized_key = requirements.academic_unit_key
  AND requirements.academic_unit_code IS DISTINCT FROM units.code;

UPDATE graduation_credit_profiles profiles
SET academic_unit_code = units.code
FROM academic_units units
WHERE units.normalized_key = profiles.academic_unit_key
  AND profiles.academic_unit_code IS DISTINCT FROM units.code;

UPDATE graduation_assessment_profiles profiles
SET academic_unit_code = units.code
FROM academic_units units
WHERE units.normalized_key = profiles.academic_unit_key
  AND profiles.academic_unit_code IS DISTINCT FROM units.code;

UPDATE graduation_legacy_requirements requirements
SET academic_unit_code = units.code
FROM academic_units units
WHERE units.normalized_key = requirements.academic_unit_key
  AND requirements.academic_unit_code IS DISTINCT FROM units.code;

UPDATE graduation_requirement_rules rules
SET academic_unit_code = units.code
FROM academic_units units
WHERE units.normalized_key = rules.academic_unit_key
  AND rules.academic_unit_code IS DISTINCT FROM units.code;

DELETE FROM section_academic_units
WHERE source_kind = 'HISTORICAL_OFFERING_CONTEXT';

INSERT INTO section_academic_units (
    semester_id,
    course_code,
    section_code,
    academic_unit_code,
    relation_type,
    source_kind
)
SELECT DISTINCT
    sections.semester_id,
    sections.course_code,
    sections.section_code,
    units.code,
    'OFFERING',
    'HISTORICAL_OFFERING_CONTEXT'
FROM sections
JOIN historical_course_offerings offerings
  ON offerings.academic_year::text = split_part(sections.semester_id, '-', 1)
 AND offerings.term_code = split_part(sections.semester_id, '-', 2)
 AND offerings.course_code = sections.course_code
 AND offerings.section_code = sections.section_code
CROSS JOIN LATERAL json_array_elements(offerings.department_contexts) AS context
JOIN academic_units units
  ON units.code = context ->> 'departmentCode'
WHERE context ->> 'departmentCode' IS NOT NULL
ON CONFLICT DO NOTHING;
