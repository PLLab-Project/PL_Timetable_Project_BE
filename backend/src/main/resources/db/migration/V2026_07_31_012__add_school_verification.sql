ALTER TABLE student_profiles
    ADD COLUMN school_verified_at timestamptz;

-- 기존 OTP 로그인 사용자는 이미 학교 이메일 소유 확인을 마쳤으므로 인증 상태를 보존합니다.
UPDATE student_profiles
   SET school_verified_at = COALESCE(updated_at, created_at, now())
 WHERE student_number IS NOT NULL;

COMMENT ON COLUMN student_profiles.school_verified_at IS
    '학교 이메일 OTP로 현재 학번 소유를 확인한 시각. Google 이메일 인증과 별도로 관리한다.';
