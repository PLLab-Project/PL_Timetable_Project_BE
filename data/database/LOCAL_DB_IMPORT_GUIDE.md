# 전체 학사 데이터 로컬 DB 적재

## 1. 준비

최신 백엔드 저장소를 받은 뒤 데이터 번들을 다음 경로에 둡니다.

```text
data/database/academic-data-bundle.tar.gz
```

로컬 환경에는 Docker Engine과 Docker Compose v2가 필요합니다.

## 2. 자동 적재

저장소 루트에서 다음 명령을 실행합니다.

```bash
./load-db.sh
```

스크립트는 다음 순서로 처리합니다.

1. 번들 압축파일 구성과 SHA-256 검증
2. PostgreSQL 컨테이너 시작
3. Flyway DB 마이그레이션 적용
4. 과거 강의·교육과정·졸업요건 기준 데이터 적재
5. 2026-1 및 공식 2026-2 강의 데이터 적재
6. 전체 학기의 `courses`, `sections`, `sessions` 정규화
7. 테이블별 기대 행 수와 참조 무결성 검증

## 3. 적재 확인

```bash
docker compose exec -T db sh -lc \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "SELECT count(*) AS course_count FROM courses;"'
```

현재 데이터 번들의 정상 결과는 `courses` 13,345건입니다. 적재 과정 마지막에
`database verification passed`가 출력되면 전체 검증이 완료된 것입니다.
