-- StudentProfile 생성자와 UserService.update()는 2026-08-03(c7cca2b)부터 student_type을
-- "DOMESTIC"으로 기본 적용한다. 하지만 그 전에 가입해서 이후 한 번도 /users/me를
-- PATCH하지 않은 계정은 여전히 student_type이 NULL로 남아있어, GraduationService.evaluate()가
-- "학생 구분" 누락으로 400을 던진다. 기존 행만 한 번 채운다 — 앞으로 생성되는 행은
-- 애플리케이션 코드가 이미 기본값을 적용한다.
UPDATE student_profiles
SET student_type = 'DOMESTIC'
WHERE student_type IS NULL;
