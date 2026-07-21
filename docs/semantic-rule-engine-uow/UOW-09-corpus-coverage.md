# UOW-09 실제 저장소 출력 커버리지

## 목표

RealEstate와 ddd-start2에서 그래프에 존재하지만 최종 RULE 출력에서 소실되는 조건을 의미와 근거를 유지한 채 출력한다.

## 실행 순서

### UOW-09A Spring 응답 metadata

- 일반 Java 메서드에는 기본 상태를 생성하지 않는다.
- Spring MVC handler임이 구조 fact로 확인되고 명시적 정상 상태가 없으면 framework-derived `200`을 생성한다.
- 명시적 `ResponseEntity`, `@ResponseStatus`, 충돌 상태가 있으면 기존 metadata를 우선한다.
- 응답 assertion에는 handler 또는 mapping annotation evidence를 포함한다.

### UOW-09B Spring Data Optional lookup

- `Repository` 하위 타입의 `findById(...).orElseThrow()`를 Spring Data lookup으로 분류한다.
- 호출 인자를 controller 입력까지 역추적해 `orderNo` 같은 target path를 보존한다.
- 매핑 실패 시 JDK Optional로 오분류하지 않고 `PARTIAL` 및 diagnostic을 남긴다.

### UOW-09C unresolved와 excluded-rule 정책

- request origin을 잃은 null/empty guard는 business restriction으로 바꾸지 않는다.
- 실제 business restriction인 null guard는 의미를 소실하지 않고 명시적 제외 사유로 보존한다.
- `SEMANTIC_UNRESOLVED`와 `EXCLUDED_RULE_NOT_ALLOWLISTED`를 후보별로 제거하거나 구체적인 미지원 diagnostic으로 바꾼다.

### UOW-09D evidence 정규화

- 같은 `nodeId`와 역할의 evidence는 후보 생성 경계에서 한 번만 보존한다.
- enum 상태 상수가 여러 경로에서 발견돼도 출력 evidence에는 중복되지 않는다.

### UOW-09E 조건부 복합 검증 출력

- `CompositeValidationClassifier` 결과를 semantic dispatch에 연결한다.
- `JEONSE -> deposit > 0`과 `MONTHLYRENT -> deposit > 0 AND rent > 0`의 활성 조건을 보존한다.
- 조건부 모델이 실행 DSL에 없으므로 숫자 조건을 무조건적인 `requestPreconditions`으로 평탄화하지 않는다.
- 실행 불가능한 조건부 규칙은 `excludedBusinessRules`에 guard와 requirement evidence를 함께 보존한다.

### UOW-09F 회귀 및 평가

- RealEstate 조건부 검증 RED 테스트를 GREEN으로 만든다.
- ddd-start2 대표 pipeline 테스트를 GREEN으로 만든다.
- 관련 단위 테스트와 전체 테스트를 순서대로 실행한다.

## 완료 조건

- Spring handler의 정상 응답 metadata가 evidence와 함께 출력된다.
- Spring Data Optional lookup은 구체 rule ID와 입력 target을 가진다.
- 지원 범위의 후보가 `SEMANTIC_UNRESOLVED` 또는 allowlist 누락으로 소실되지 않는다.
- 상태 evidence에 중복 node가 없다.
- RealEstate 복합 검증이 조건 의미를 잃지 않고 최종 출력에 남는다.

