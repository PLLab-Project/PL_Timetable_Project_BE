# Database conventions

## 원칙

1. 운영 스키마 변경은 `backend/src/main/resources/db/migration`의 Flyway 파일로만 수행합니다.
2. 이미 공유된 마이그레이션은 수정하지 않고 새 버전을 추가합니다.
3. Hibernate `ddl-auto`는 `validate`를 유지합니다. `create`, `update`는 사용하지 않습니다.
4. `schema.sql`, `data.sql`과 Flyway를 혼용하지 않습니다.
5. 새 사용자 소유 데이터의 PK는 UUID를 기본으로 합니다. 이미 안정적으로 생성된 외부
   원천 ID는 문자열 형식을 보존할 수 있습니다.
6. 시각은 `timestamptz`, 날짜는 `date`, 학점은 정확한 `numeric`을 우선 사용합니다.
7. JSON은 원천 보존·가변 부가정보에만 사용하고 검색·조인 기준은 열과 FK로 정규화합니다.
8. FK 삭제 정책은 데이터 수명주기에 따라 결정합니다. 현재 사용자 종속 데이터는 주로
   `CASCADE`, 이수과목의 과거 개설 참조는 `SET NULL`을 사용합니다.

## Flyway 파일명

`VYYYY_MM_DD_NNN__lower_snake_case_description.sql`

날짜와 일련번호를 함께 사용해 병렬 브랜치 충돌 가능성을 낮춥니다. PR 전에 반드시
`origin/main`을 갱신하고 같은 버전이 추가되지 않았는지 확인합니다.

## DB 변경 PR 체크리스트

- 새 빈 PostgreSQL 18 DB에서 전체 마이그레이션 성공
- 기존 데이터가 있는 DB를 변경하는 마이그레이션이면 업그레이드 경로 확인
- FK, UNIQUE, CHECK, 조회 인덱스 검토
- API DTO와 nullable/default 계약 검토
- 담당자 교차 리뷰
- 인증·권한 관련 변경은 Yuki 최종 확인

`main`에는 직접 커밋·푸시하지 않고 기능 브랜치와 PR을 사용합니다. 강제 푸시와 공유
마이그레이션 재작성은 금지합니다.
