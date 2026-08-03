CREATE TABLE student_academic_programs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    academic_unit_code varchar(40) NOT NULL
        REFERENCES academic_units(code) ON DELETE RESTRICT,
    program_role varchar(32) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    display_order smallint NOT NULL DEFAULT 0,
    started_semester_id varchar(20),
    completed_semester_id varchar(20),
    verified_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_student_academic_program_role CHECK (
        program_role IN ('PRIMARY', 'DOUBLE_MAJOR', 'MINOR', 'MICRO_MAJOR')
    ),
    CONSTRAINT ck_student_academic_program_status CHECK (
        status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_student_academic_program_order CHECK (display_order >= 0),
    CONSTRAINT uq_student_academic_program_unit UNIQUE (user_id, academic_unit_code),
    CONSTRAINT uq_student_academic_program_order UNIQUE (user_id, display_order)
);

CREATE UNIQUE INDEX uq_student_academic_program_primary
    ON student_academic_programs (user_id)
    WHERE program_role = 'PRIMARY' AND status <> 'WITHDRAWN';

CREATE INDEX ix_student_academic_program_lookup
    ON student_academic_programs (user_id, status, display_order);

INSERT INTO student_academic_programs (
    user_id,
    academic_unit_code,
    program_role,
    status,
    display_order
)
SELECT user_id, academic_unit_code, 'PRIMARY', 'ACTIVE', 0
  FROM student_profiles
 WHERE academic_unit_code IS NOT NULL
ON CONFLICT (user_id, academic_unit_code) DO NOTHING;

UPDATE student_profiles
   SET student_type = 'DOMESTIC',
       program_path = coalesce(program_path, 'ADVANCED_MAJOR'),
       updated_at = now()
 WHERE academic_unit_code IS NOT NULL
   AND (student_type IS NULL OR program_path IS NULL);

ALTER TABLE student_profiles
    ALTER COLUMN student_type SET DEFAULT 'DOMESTIC';

COMMENT ON TABLE student_academic_programs IS
    'A variable-length list of primary, double-major, minor, and micro-major programs pursued by a student.';
COMMENT ON COLUMN student_academic_programs.program_role IS
    'The role of this program for the student; limits are enforced by policy rather than fixed columns.';
