# Backend architecture

## 패키지 기준

백엔드는 기능 단위 패키지를 우선하고, 각 기능 내부에서 역할을 분리합니다.

```text
academic/
  department/
    controller
    service
    query repository
    dto/
  semester/
  course/
  review/
  graduation/
completedcourse/
timetable/
optimization/
```

- Controller: HTTP 파라미터 해석과 응답 반환
- Service: 입력 검증, 트랜잭션 경계, 업무 규칙
- Query Repository: 읽기 전용 SQL과 응답 projection
- JPA Repository/Entity: 사용자 소유 데이터의 생성·수정·삭제
- DTO: API 계약이며 DB Entity를 직접 반환하지 않음

## 데이터 접근

학과·학기·강의·분반처럼 검색·필터·집계가 중심인 기준 데이터는
`NamedParameterJdbcTemplate`의 명시적인 SQL projection으로 조회합니다. 리뷰·이수과목처럼
상태 변경과 생명주기가 있는 사용자 데이터는 JPA Entity와 Repository를 사용합니다.

스키마 변경은 Flyway 마이그레이션만 사용합니다. Hibernate는
`spring.jpa.hibernate.ddl-auto=validate`로 DB 계약을 검증하며 DDL을 생성하지 않습니다.

## API 원칙

- 목록 API는 서버 페이지네이션을 사용하며 `size`는 최대 100입니다.
- 정렬문은 요청값을 SQL에 직접 넣지 않고 허용된 enum을 SQL 조각으로 변환합니다.
- 학사 데이터는 `semester_id`, `course_code`, `section_code`, `academic_unit_code`를 정식 키로 사용합니다.
- 과목명·교수·학점·수업시간은 클라이언트 입력을 신뢰하지 않고 학사 DB에서 조회합니다.
- API 요청·응답은 Java record를 우선 사용합니다.
- 사용자 소유 API의 사용자 ID는 요청값으로 받지 않고
  `AuthenticatedUser.userId()`에서 가져옵니다.
- 실행 중인 API 스키마는 springdoc OpenAPI로 생성하며, Scalar를 기본 화면으로 사용합니다.
  사용법과 문서 경계는 [OpenAPI·API 문서 사용법](OPENAPI.md)을 따릅니다.

## 공통 코드 경계

세션 인증 주체는 `AuthenticatedUser`로 통합되어 있습니다. 다만 성공 응답은 인증 API의
`ApiResponse<T>` 형식과 다른 도메인의 직접 DTO 형식이 함께 존재하고, 예외 응답도
`common.exception`과 기존 `exception` 패키지의 두 형식이 공존합니다. 프론트 최종 연동
전에 팀 공통 응답 형식과 오류 코드를 하나로 확정해야 합니다.

범용 BaseController, BaseService, BaseRepository 같은 계층은 만들지 않습니다. 공통화는
두 개 이상의 기능에서 실제로 동일한 요구가 확인된 경우에만 진행합니다.
