# Stage 1: Planner — Unit 01 Fact Code Graph

## 실행 체크리스트

- [x] Functional/NFR 설계 결정 로드
- [x] 기존 graph와 type resolver 경계 확인
- [x] Intent Diff 작성
- [x] domain model 파일 계획 작성
- [x] builder/support 파일 계획 작성
- [x] example/PBT 검증 계획 작성

## Intent Diff

| 영역 | AS-IS | TO-BE |
|:---|:---|:---|
| Graph 의미 | `BUSINESS_RULE`과 분류 의미가 graph 생성 시 포함됨 | 관찰 사실만 포함하는 별도 `FactCodeGraph` 병렬 생성 |
| Source 위치 | 단일 line 중심 | start/end line과 column 보존 |
| 조건 구조 | snippet과 label 중심 | operand, receiver, argument, branch를 typed edge로 보존 |
| 타입 해석 | 일부 내부 helper에서 사용 | node별 `RESOLVED/PARTIAL/UNRESOLVED/NOT_APPLICABLE` 공개 |
| Traversal | legacy depth budget 12 | 독립 depth 5, method 100, edge 300 복합 budget |
| 중단 결과 | 조용히 누락될 수 있음 | API별 truncation diagnostic |
| ID | 의미 label과 문자열 조합 | source range와 AST role 기반 결정적 hash ID |
| 테스트 | example-based 중심 | example-based + jqwik property test |

## 구현 원칙

1. 기존 graph domain과 builder를 변경하거나 제거하지 않는다.
2. 신규 domain package는 JavaParser와 legacy graph에 의존하지 않는다.
3. JavaParser AST를 domain object에 보관하지 않는다.
4. snippet을 다시 파싱해 구조를 복원하지 않는다.
5. 외부 method body는 방문하지 않지만 호출 사실은 기록한다.
6. budget 중단과 resolution 실패를 typed diagnostic으로 보존한다.

## File Plan

### 1. `build.gradle`

- **Change Type**: Modify
- `testImplementation 'net.jqwik:jqwik:1.7.4'` 추가
- 기존 JUnit, launcher, AssertJ 버전 유지

### 2. `src/main/java/io/atworks/specscan/analysis/domain/fact/FactGraphTypes.java`

- **Change Type**: New
- package-private가 아닌 공개 enum을 한 파일에 둘 수 없으므로 실제 구현에서는 다음 enum 파일로 분리한다.
- `FactNodeType.java`, `FactEdgeType.java`, `TypeResolutionStatus.java`, `DiagnosticSeverity.java`, `TraversalDecision.java`

### 3. `src/main/java/io/atworks/specscan/analysis/domain/fact/SourceRange.java`

- **Change Type**: New
- 상대 경로와 start/end line/column 검증

### 4. `src/main/java/io/atworks/specscan/analysis/domain/fact/TypeResolution.java`

- **Change Type**: New
- status별 qualified type/signature invariant와 factory methods

### 5. `src/main/java/io/atworks/specscan/analysis/domain/fact/FactNodePayload.java`

- **Change Type**: New
- sealed interface와 nested immutable payload records
- method, parameter, local, field, enum, call, condition, outcome payload 포함

### 6. `src/main/java/io/atworks/specscan/analysis/domain/fact/FactNode.java`

- **Change Type**: New
- 공통 identity/source/type/payload와 node type/payload 조합 검증

### 7. `src/main/java/io/atworks/specscan/analysis/domain/fact/FactEdge.java`

- **Change Type**: New
- source/target/type/ordinal/role 구조

### 8. `src/main/java/io/atworks/specscan/analysis/domain/fact/FactCodeGraph.java`

- **Change Type**: New
- immutable collection, duplicate ID 및 dangling edge 검증

### 9. `src/main/java/io/atworks/specscan/analysis/domain/fact/FactGraphTraversalBudget.java`

- **Change Type**: New
- 기본값 5/100/300과 양수 검증

### 10. `src/main/java/io/atworks/specscan/analysis/domain/fact/FactGraphTraversalStats.java`

- **Change Type**: New
- observed depth/method/edge count

### 11. `src/main/java/io/atworks/specscan/analysis/domain/fact/FactGraphDiagnostic.java`

- **Change Type**: New
- reason, truncation, budget, observed stats, source evidence

### 12. `src/main/java/io/atworks/specscan/analysis/domain/fact/FactGraphBuildResult.java`

- **Change Type**: New
- graph와 diagnostic의 immutable result

### 13. `src/main/java/io/atworks/specscan/analysis/support/fact/DeterministicFactNodeIdGenerator.java`

- **Change Type**: New
- canonical UTF-8 input과 SHA-256 기반 node/edge ID 생성
- OS path separator와 locale 독립성 보장

### 14. `src/main/java/io/atworks/specscan/analysis/support/fact/DefaultMethodTraversalPolicy.java`

- **Change Type**: New
- project source, base package, external/framework/generated/repository/accessor 판정

### 15. `src/main/java/io/atworks/specscan/analysis/support/fact/FactGraphAccumulator.java`

- **Change Type**: New
- atomic node/edge 추가, edge budget, collision, immutable snapshot

### 16. `src/main/java/io/atworks/specscan/analysis/support/fact/FactExpressionVisitor.java`

- **Change Type**: New
- condition operand, method receiver/arguments, field/Enum access 추출

### 17. `src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java`

- **Change Type**: New
- parameter/local/call/condition/throw/return 추출과 내부 방문 후보 전달

### 18. `src/main/java/io/atworks/specscan/analysis/support/fact/DefaultFactCodeGraphBuilder.java`

- **Change Type**: New
- API별 root resolution, traversal context, composite budget, cycle guard, diagnostic 조립
- 기존 `TypeResolver`의 source resolution 기능은 재사용하되 legacy graph를 사용하지 않음

### 19. `src/main/java/io/atworks/specscan/analysis/support/fact/FactGraphIntegrityValidator.java`

- **Change Type**: New
- ID, edge, payload, budget, truncation invariant 검증

### 20. `src/test/java/io/atworks/specscan/analysis/fact/FactDomainModelTest.java`

- **Change Type**: New
- immutable collection, payload 조합, dangling edge, budget 검증

### 21. `src/test/java/io/atworks/specscan/analysis/fact/FactNodeIdGeneratorProperties.java`

- **Change Type**: New
- 동일 입력 결정성, role/range 분리, path normalization property

### 22. `src/test/java/io/atworks/specscan/analysis/fact/FactCodeGraphBuilderTest.java`

- **Change Type**: New
- boolean guard, renamed guard, receiver/arguments, branch polarity, type failure, 외부 호출 기록 검증

### 23. `src/test/java/io/atworks/specscan/analysis/fact/FactTraversalBudgetTest.java`

- **Change Type**: New
- depth/method/edge 한도, 순환, truncation diagnostic 검증

## 구현 순서

1. domain invariants와 ID generator
2. accumulator와 integrity validator
3. expression/method visitor
4. traversal policy와 builder orchestration
5. example-based builder/budget test
6. jqwik properties
7. 전체 회귀 테스트

## Verification Plan

- domain 및 ID generator 단위 테스트
- jqwik property 최소 500 tries
- builder fixture test
- 각 budget 한도와 cycle test
- 기존 Unit 00 baseline test
- 전체 `gradlew test`
- `git diff --check`

## PBT Compliance

- PBT-01~04, 07, 08, 10 적용
- PBT-05, 06은 N/A
- PBT-09는 jqwik 1.7.4로 충족

