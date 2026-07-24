ALTER TABLE student_profiles
    ADD COLUMN academic_unit_code varchar(40);

UPDATE student_profiles profile
SET academic_unit_code = (
    SELECT candidate.code
    FROM (
        SELECT unit.code, 1 AS priority, unit.is_current
        FROM academic_units unit
        WHERE unit.code = profile.academic_unit_key

        UNION ALL

        SELECT unit.code, 2 AS priority, unit.is_current
        FROM academic_units unit
        WHERE unit.normalized_key = normalize_academic_unit_key(
            COALESCE(profile.academic_unit_key, profile.academic_unit_name)
        )

        UNION ALL

        SELECT unit.code, 3 AS priority, unit.is_current
        FROM academic_unit_aliases alias
        JOIN academic_units unit ON unit.code = alias.academic_unit_code
        WHERE alias.alias_key = normalize_academic_unit_key(
            COALESCE(profile.academic_unit_key, profile.academic_unit_name)
        )
          AND (
              profile.admission_year IS NULL
              OR alias.valid_from_year IS NULL
              OR alias.valid_from_year <= profile.admission_year
          )
          AND (
              profile.admission_year IS NULL
              OR alias.valid_to_year IS NULL
              OR alias.valid_to_year >= profile.admission_year
          )
    ) candidate
    ORDER BY candidate.priority, candidate.is_current DESC, candidate.code
    LIMIT 1
)
WHERE profile.academic_unit_key IS NOT NULL
   OR profile.academic_unit_name IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM student_profiles
        WHERE (academic_unit_key IS NOT NULL OR academic_unit_name IS NOT NULL)
          AND academic_unit_code IS NULL
    ) THEN
        RAISE EXCEPTION
            'student_profiles contains academic units that cannot be resolved to academic_units';
    END IF;
END
$$;

ALTER TABLE student_profiles
    ADD CONSTRAINT fk_student_profiles_academic_unit
        FOREIGN KEY (academic_unit_code)
        REFERENCES academic_units(code)
        ON DELETE RESTRICT;

DROP INDEX ix_student_profiles_requirement_lookup;

ALTER TABLE student_profiles
    DROP COLUMN academic_unit_name,
    DROP COLUMN academic_unit_key;

CREATE INDEX ix_student_profiles_requirement_lookup
    ON student_profiles (
        academic_unit_code,
        admission_year,
        student_type,
        program_path
    );

CREATE INDEX ix_timetables_semester
    ON timetables (semester_id);

CREATE INDEX ix_sessions_room
    ON sessions (semester_id, room_code)
    WHERE room_code IS NOT NULL;

CREATE INDEX ix_optimization_required_sections_section
    ON optimization_job_required_sections (
        semester_id,
        course_code,
        section_code
    );

COMMENT ON COLUMN student_profiles.academic_unit_code IS
    'Canonical academic unit selected from academic_units; display names are resolved by join.';
