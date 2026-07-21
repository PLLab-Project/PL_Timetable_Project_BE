CREATE TABLE course_reviews (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_code varchar(40) NOT NULL,
    course_name varchar(240) NOT NULL,
    professor varchar(120),
    semester varchar(20) NOT NULL,
    rating smallint NOT NULL,
    content text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_course_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_course_reviews_author_offering
        UNIQUE NULLS NOT DISTINCT (user_id, course_code, professor, semester)
);

CREATE INDEX ix_course_reviews_course_professor
    ON course_reviews (course_code, professor);
CREATE INDEX ix_course_reviews_user_created
    ON course_reviews (user_id, created_at DESC);

CREATE TABLE completed_courses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_code varchar(40),
    course_name varchar(240) NOT NULL,
    credits numeric(5, 2) NOT NULL,
    category varchar(160) NOT NULL,
    area varchar(120),
    semester varchar(20),
    status varchar(24) NOT NULL DEFAULT 'COMPLETED',
    historical_offering_id varchar(36)
        REFERENCES historical_course_offerings(id) ON DELETE SET NULL,
    section_code varchar(40),
    input_source varchar(32) NOT NULL DEFAULT 'MANUAL',
    source_snapshot jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_completed_courses_credits CHECK (credits >= 0),
    CONSTRAINT ck_completed_courses_status CHECK (
        status IN ('COMPLETED', 'IN_PROGRESS', 'PLANNED', 'FAILED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_completed_courses_input_source CHECK (
        input_source IN ('MANUAL', 'TIMETABLE', 'OCR', 'IMPORT')
    )
);

CREATE INDEX ix_completed_courses_user_id ON completed_courses (user_id);
CREATE INDEX ix_completed_courses_user_semester ON completed_courses (user_id, semester);
CREATE INDEX ix_completed_courses_user_status ON completed_courses (user_id, status);
CREATE INDEX ix_completed_courses_historical_offering_id
    ON completed_courses (historical_offering_id);

COMMENT ON TABLE course_reviews IS 'Course review domain table; author identity references the shared users contract.';
COMMENT ON TABLE completed_courses IS 'User-owned completion record used as input for graduation-requirement assessment.';
