CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name varchar(120),
    primary_email varchar(320),
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'))
);

CREATE INDEX ix_users_primary_email ON users (lower(primary_email))
    WHERE primary_email IS NOT NULL;

CREATE TABLE social_identities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider varchar(32) NOT NULL,
    provider_subject varchar(255) NOT NULL,
    email varchar(320),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_social_identity_provider_subject UNIQUE (provider, provider_subject)
);

CREATE INDEX ix_social_identities_user_id ON social_identities (user_id);

CREATE TABLE student_profiles (
    user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    student_number varchar(20),
    academic_unit_name varchar(240),
    academic_unit_key varchar(240),
    grade smallint,
    admission_year integer,
    entry_type varchar(24),
    student_type varchar(24),
    section_group varchar(24),
    program_path varchar(32),
    profile_completed boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_profiles_student_number UNIQUE (student_number),
    CONSTRAINT ck_student_profiles_grade CHECK (grade IS NULL OR grade BETWEEN 1 AND 6),
    CONSTRAINT ck_student_profiles_admission_year CHECK (
        admission_year IS NULL OR admission_year BETWEEN 1900 AND 2100
    )
);

CREATE INDEX ix_student_profiles_requirement_lookup
    ON student_profiles (academic_unit_key, admission_year, student_type, program_path);

CREATE TABLE privacy_consents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    consent_type varchar(40) NOT NULL DEFAULT 'PRIVACY_POLICY',
    consent_version varchar(40) NOT NULL,
    agreed boolean NOT NULL,
    agreed_at timestamptz NOT NULL DEFAULT now(),
    withdrawn_at timestamptz,
    CONSTRAINT uq_privacy_consents_version UNIQUE (user_id, consent_type, consent_version),
    CONSTRAINT ck_privacy_consents_withdrawal CHECK (
        withdrawn_at IS NULL OR withdrawn_at >= agreed_at
    )
);

CREATE INDEX ix_privacy_consents_user_agreed
    ON privacy_consents (user_id, agreed_at DESC);

COMMENT ON TABLE social_identities IS
    'Authentication identity mapping owned by the authentication/security domain; no OAuth tokens are stored here.';
COMMENT ON TABLE student_profiles IS
    'Academic profile separated from authentication identity so social-login providers remain replaceable.';
