ALTER TABLE timetables
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

COMMENT ON COLUMN timetables.version IS
    '낙관적 잠금 버전. Timetable과 하위 timetable_courses 컬렉션(분반 추가/삭제/전체
    교체)을 하나의 집합체로 보호한다 — 같은 시간표를 동시에 수정하는 두 요청 중
    하나는 이 버전 충돌로 감지되어 409로 응답한다(GlobalExceptionHandler).';
