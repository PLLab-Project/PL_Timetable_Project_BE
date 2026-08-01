-- Spring Session의 사용자 식별자가 변경되더라도 안전하게 저장할 수 있도록 여유를 둡니다.
-- AuthenticatedUser.getName()은 UUID 36자를 반환하지만 기존 운영 데이터와 확장성도 보존합니다.
ALTER TABLE spring_session
    ALTER COLUMN principal_name TYPE varchar(255);
