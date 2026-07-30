ALTER TABLE student_profiles
    ADD COLUMN tutorial_completed boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN student_profiles.tutorial_completed IS
    '사용자 기기 간에 동기화하는 최초 사용 튜토리얼 완료 상태.';

ALTER TABLE timetables
    ADD COLUMN is_favorite boolean NOT NULL DEFAULT false;

CREATE INDEX ix_timetables_user_favorite_updated
    ON timetables (user_id, is_favorite DESC, updated_at DESC);

ALTER TABLE completed_courses
    ADD COLUMN grading_basis varchar(20) NOT NULL DEFAULT 'LETTER',
    ADD COLUMN grade_value varchar(12);

ALTER TABLE completed_courses
    ADD CONSTRAINT ck_completed_courses_grading_basis
        CHECK (grading_basis IN ('LETTER', 'PASS_FAIL')),
    ADD CONSTRAINT ck_completed_courses_grade_value
        CHECK (
            grade_value IS NULL
            OR btrim(grade_value) <> ''
        ),
    ADD CONSTRAINT ck_completed_courses_pass_fail_value
        CHECK (
            grading_basis <> 'PASS_FAIL'
            OR grade_value IS NULL
            OR grade_value IN ('P', 'N')
        );

COMMENT ON COLUMN completed_courses.credits IS
    '성적 방식과 무관한 교과목 인정 학점. P/N 과목도 실제 인정 학점을 저장한다.';
COMMENT ON COLUMN completed_courses.grading_basis IS
    'LETTER 또는 PASS_FAIL. 인정 학점(credits)과 성적 표기(grade_value)를 분리한다.';

CREATE TABLE optimization_job_available_times (
    job_id bigint NOT NULL REFERENCES optimization_jobs(id) ON DELETE CASCADE,
    position integer NOT NULL,
    start_minute smallint NOT NULL,
    end_minute smallint NOT NULL,
    CONSTRAINT pk_optimization_job_available_times
        PRIMARY KEY (job_id, position),
    CONSTRAINT ck_optimization_job_available_times_position
        CHECK (position >= 0),
    CONSTRAINT ck_optimization_job_available_times_range
        CHECK (
            start_minute >= 0
            AND start_minute < end_minute
            AND end_minute <= 1440
        )
);

CREATE TABLE optimization_job_blocked_times (
    job_id bigint NOT NULL REFERENCES optimization_jobs(id) ON DELETE CASCADE,
    position integer NOT NULL,
    day_of_week varchar(20) NOT NULL,
    start_minute smallint NOT NULL,
    end_minute smallint NOT NULL,
    CONSTRAINT pk_optimization_job_blocked_times
        PRIMARY KEY (job_id, position),
    CONSTRAINT ck_optimization_job_blocked_times_position
        CHECK (position >= 0),
    CONSTRAINT ck_optimization_job_blocked_times_day
        CHECK (
            day_of_week IN (
                'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
                'FRIDAY', 'SATURDAY', 'SUNDAY'
            )
        ),
    CONSTRAINT ck_optimization_job_blocked_times_range
        CHECK (
            start_minute >= 0
            AND start_minute < end_minute
            AND end_minute <= 1440
        )
);

INSERT INTO optimization_job_available_times (
    job_id, position, start_minute, end_minute
)
SELECT id, 0, available_start_minute, available_end_minute
  FROM optimization_jobs;

COMMENT ON TABLE optimization_job_available_times IS
    '자동편성에서 선택한 복수의 하루 수강 가능 시간 범위.';
COMMENT ON TABLE optimization_job_blocked_times IS
    '자동편성 결과에서 반드시 비워 둘 요일별 시간 범위.';
