# Semantic Rule Engine Action Items

## P0: 정확도와 안전성

### AI-01 Authorization 하드코딩 격리

- 기본 Rule Pack에서 `AuthorizationGuardCallRule`을 비활성화할 수 있는 characterization test를 만든다.
- `canEdit`, `canView`, `isAuthorized`가 모두 `HAS_CANCELLATION_PERMISSION`이 되는 현재 오탐을 고정한다.
- 권한 종류를 증명할 annotation, qualified signature 또는 사용자 정의 descriptor가 없으면 unresolved로 처리한다.

### AI-02 의미 손실 없는 중간 모델

- `SemanticPredicate`에 expression tree, failure polarity, input path, method identity, evidence, resolution quality를 저장한다.
- `NormalizedConstraint`로 변환하기 전에 AND/OR, enum case, numeric boundary를 보존한다.
- ContractDetail의 null, JEONSE/deposit, MONTHLYRENT/rent 조건을 acceptance corpus로 사용한다.
- 1차 expression 범위는 binary, unary `!`, method call과 명시된 leaf로 제한하고 unsupported 표현식은 diagnostic과 `PARTIAL`로 보존한다.

### AI-03 Detector와 Classifier 책임 분리

- Detector는 실패 outcome과 validation sink seed만 찾는다.
- `requireNonNull`, `PasswordEncoder`, `Optional`, Spring Data의 의미 분류는 Detector에서 제거한다.
- 제거 전후 후보 누락과 false positive 수를 비교한다.
- 먼저 contributor adapter로 기존 seed 동작을 격리하고, 대응 classifier shadow 검증 후 항목별로 제거한다.

## P1: 구조 단순화

### AI-04 공유 Graph Index

- graph별 node, outgoing/incoming edge, operand, call target, outcome, origin index를 한 번 생성한다.
- 각 classifier가 `new StructuralRuleSupport(graph)`를 생성하지 않게 한다.
- 후보당 전체 edge scan 횟수를 계측한다.

### AI-05 Binary Constraint Classifier

- null, literal equality, enum comparison, numeric boundary를 같은 expression 구조에서 분류한다.
- `<= 0`, `< 0`, `>=`, `>`의 실패 극성과 inclusive 여부를 보존한다.
- snippet은 diagnostic 표시 외에는 의미 판정의 주 근거로 사용하지 않는다.

### AI-06 Standard Guard Method Registry

- `Objects.requireNonNull`, `Assert.notNull`, `Preconditions.checkNotNull`, 지원되는 empty/text guard를 descriptor로 관리한다.
- descriptor는 qualified signature, argument index, failure semantics, normalized operator를 가진다.
- unresolved source fallback은 별도 confidence와 diagnostic을 가진다.

### AI-07 Optional Lookup 계층형 통합

- 일반 `Optional.orElseThrow`를 fallback으로 처리한다.
- Spring Data `findById().orElseThrow()`는 더 구체적인 subtype 결과로 처리한다.
- 동일 후보에서 두 결과가 생성되지 않게 한다.

## P2: 전문 분석기와 확장

### AI-08 Delegation 책임 재정의

- `DelegatedBooleanPredicatePromoter`는 interprocedural predicate propagation만 담당한다.
- 기존 `DelegatedGuardRule`의 enum state와 version heuristic을 별도 classifier로 이동한다.
- 재귀·극성 반전·인자 매핑·순환 호출 제한을 검증한다.
- 호출 깊이는 직접 실패 메서드를 Depth 0으로 하여 최대 Depth 2로 제한한다.
- 인자 origin 일부가 실패하면 후보는 `PARTIAL`로 보존하되 실행 조건 출력은 target/operator 증명 여부로 제한한다.

### AI-09 Framework Idiom Classifier 유지

- PasswordEncoder는 resolved signature, argument origin, failure branch를 함께 검증한다.
- framework classifier가 Detector seed 생성과 의미 분류를 중복하지 않게 한다.

### AI-10 Input-Domain 의미 강화

- 일반 입력-도메인 equality와 optimistic lock 의미를 분리한다.
- `version` 문자열이 아니라 `@Version`, field identity, persistence metadata로 optimistic lock을 증명한다.

### AI-11 YAML Rule 안전 경계

- YAML matcher가 semantic payload, qualified signature, operator, polarity, origin kind만 사용하도록 한다.
- 임의 snippet contains는 명시적 fallback으로만 허용한다.

## P3: Cutover 품질 게이트

### AI-12 출력 중심 Golden Corpus

- 직접 null/empty, Optional, Spring Data, PasswordEncoder, enum, delegated boolean, input-domain, authorization 음성 사례를 포함한다.
- `PredicateCandidate`뿐 아니라 최종 endpoint output을 비교한다.

### AI-13 성능과 설명 가능성

- 후보 수, classifier dispatch 수, graph traversal 수, unresolved reason을 기록한다.
- “어떤 classifier가 왜 매칭/거절했는지” diagnostic을 제공한다.

### AI-14 단계적 삭제

- 새 classifier와 기존 Rule을 shadow mode로 비교한다.
- semantic output 동등성과 golden corpus 통과 후에만 기존 Rule 파일을 삭제한다.
- 파일 수 감소율이나 코드 줄 수를 acceptance criterion으로 사용하지 않는다.
