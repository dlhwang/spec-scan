# Semantic Rule Engine 개선 Unit of Work

## 결론

현재 개선의 성공 기준은 Java `GraphRule` 클래스 개수가 아니다. 동일한 `FactCodeGraph`를 각 Rule이 반복 탐색하고, Detector와 Rule이 같은 관용구를 중복 판정하며, 최종 API 제약에 필요한 의미가 중간 변환에서 사라지는 문제를 제거하는 것이 목표다.

따라서 다음 순서로 진행한다.

```text
기존 동작 특성화
  → 공유 SemanticContext 및 SemanticPredicate
  → Detector의 의미 분류 제거
  → binary classifier와 표준 guard registry
  → Optional 계층형 classifier
  → Authorization 하드코딩 격리
  → 핵심 graph/framework classifier 이전
  → 단일 dispatch 및 기존 GraphRule cutover
```

## 설계 판단

- `AuthorizationGuardCallRule`은 기본 pack에서 격리하는 방향이 타당하다.
- `NullRejectionGuardRule` 전체를 Map으로 치환하지 않는다. 직접 binary null 비교는 구조 classifier가 담당한다.
- 표준 guard method만 qualified signature 기반 registry로 이동한다.
- 일반 Optional과 Spring Data lookup은 하나의 계층형 classifier에서 specificity를 유지한다.
- 기존 `DelegatedGuardRule`과 boolean predicate propagation은 서로 다른 책임으로 분리한다.
- PasswordEncoder, enum guard, input-domain mismatch는 공유 semantic context를 사용하는 전문 classifier로 유지한다.
- YAML 확장은 원문 snippet 검색이 아니라 구조화된 semantic field만 대상으로 제한한다.

## 실행 순서

1. [UOW-01 현재 동작 특성화](UOW-01-characterization.md)
2. [UOW-02 SemanticContext 도입](UOW-02-semantic-context.md)
3. [UOW-03 Detector 책임 정리](UOW-03-detector-boundary.md)
4. [UOW-04 Binary classifier와 guard registry](UOW-04-binary-and-guard-registry.md)
5. [UOW-05 Optional lookup 통합](UOW-05-optional-lookup.md)
6. [UOW-06 Authorization 격리](UOW-06-authorization-quarantine.md)
7. [UOW-07 핵심 classifier 이전](UOW-07-specialized-classifiers.md)
8. [UOW-08 Engine cutover와 회귀](UOW-08-cutover-and-regression.md)
9. [UOW-09 실제 저장소 출력 커버리지](UOW-09-corpus-coverage.md)

UOW-03은 한 번에 기존 동작을 제거하는 단계가 아니다. 실행 순서는 `UOW-03A 기존 seed contributor 격리 → UOW-04/05 shadow classifier → UOW-03B 동등성이 검증된 의미 판정 제거`로 해석한다.

## 공통 원칙

- 각 UoW는 RED 테스트, 최소 구현, 회귀 검증을 독립적으로 완료한다.
- 기존 Rule 삭제는 동일한 semantic output이 새 경로에서 검증된 후에만 수행한다.
- resolved signature와 graph edge가 있으면 snippet fallback보다 우선한다.
- fallback 결과는 `PARTIAL` 또는 명시적 diagnostic으로 구분한다.
- API endpoint 귀속, JSONPath, operator, expected value, evidence가 기존보다 나빠지면 cutover하지 않는다.
- `PARTIAL` predicate는 보존하되 target과 operator를 증명하지 못하면 실행 가능한 API 조건으로 출력하지 않는다.
- 성능은 Rule 파일 수가 아니라 후보당 graph traversal 횟수와 graph index 생성 횟수로 측정한다.
