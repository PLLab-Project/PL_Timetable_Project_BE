-- 학교 이메일 OTP 로그인에 필요한 일회성 인증 기록입니다.
-- 기존 사용자·학사 테이블을 수정하지 않고 인증 도메인 전용 테이블로 분리합니다.
CREATE TABLE login_otp_challenges (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    student_number varchar(20) NOT NULL,
    email varchar(320) NOT NULL,
    code_hash varchar(100) NOT NULL,
    failed_attempts integer NOT NULL DEFAULT 0,
    expires_at timestamptz NOT NULL,
    resend_available_at timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_login_otp_failed_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT ck_login_otp_expiration CHECK (expires_at > created_at),
    CONSTRAINT ck_login_otp_resend CHECK (resend_available_at >= created_at),
    CONSTRAINT ck_login_otp_consumed CHECK (
        consumed_at IS NULL OR consumed_at >= created_at
    )
);

CREATE INDEX ix_login_otp_student_created
    ON login_otp_challenges (student_number, created_at DESC);

CREATE INDEX ix_login_otp_expiration
    ON login_otp_challenges (expires_at)
    WHERE consumed_at IS NULL;

COMMENT ON TABLE login_otp_challenges IS
    'Short-lived school-email OTP challenges. Only password hashes, never raw OTP codes, are stored.';
