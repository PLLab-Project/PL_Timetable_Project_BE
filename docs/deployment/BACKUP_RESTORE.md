# DB 백업·복구

운영 DB 백업은 Git에 포함하지 않습니다. 백업 파일에는 사용자 개인정보와 학사 사용
기록이 포함될 수 있으므로 접근 권한이 제한된 저장소에 암호화해 보관합니다.

## 백업

```bash
./scripts/backup-database.sh
```

기본 출력:

```text
backups/pl_timetable_YYYYMMDD_HHMMSS.dump
```

경로를 지정할 수도 있습니다.

```bash
./scripts/backup-database.sh /secure/path/pl_timetable.dump
```

스크립트는 PostgreSQL custom format과 `--no-owner`를 사용합니다. `backups/`는
`.gitignore` 대상입니다.

백업 검증:

```bash
docker compose exec -T db pg_restore --list \
  </secure/path/pl_timetable.dump >/dev/null
sha256sum /secure/path/pl_timetable.dump \
  >/secure/path/pl_timetable.dump.sha256
```

## 복구

복구는 기존 DB 내용을 덮어쓸 수 있으므로 명시적인 확인 값이 필요합니다.

```bash
CONFIRM_RESTORE=pl_timetable \
  ./scripts/restore-database.sh /secure/path/pl_timetable.dump
```

스크립트는 다음 순서로 동작합니다.

1. API 중지
2. `pg_restore --clean --if-exists --no-owner`
3. API 재시작
4. healthcheck

복구 후 확인:

```bash
docker compose run --rm migrate info
docker compose exec -T api \
  curl -fsS http://127.0.0.1:8080/api/v1/health/live
docker compose exec -T db sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"'
```

## 권장 정책

- 배포 직전 수동 백업
- 하루 1회 이상 자동 백업
- 최소 한 개는 서버 외부 위치에 보관
- 보관 기간과 개인정보 삭제 정책을 팀에서 확정
- 정기적으로 별도 DB에서 복원 시험
- 백업 파일·체크섬·복원 시험일을 함께 기록
