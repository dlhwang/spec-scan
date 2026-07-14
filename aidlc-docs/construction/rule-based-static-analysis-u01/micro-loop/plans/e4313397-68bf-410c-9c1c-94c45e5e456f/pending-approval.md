# Unit 01 Fact Code Graph 최종 개발 및 검증 계획서

## Metadata

- **Unit Slug**: `rule-based-static-analysis-u01`
- **Plan UUID**: `e4313397-68bf-410c-9c1c-94c45e5e456f`
- **Date Finalized**: `2026-07-14T02:30:34.866Z`
- **Review Pipeline**: Planner PASS | Critic OKAY | Architect APPROVE | Reconciliation PASS
- **Status**: PENDING USER APPROVAL

## ADR

### Decision

기존 evidence graph와 별개로 immutable Fact Code Graph domain과 AST visitor 기반 builder를 도입하고, 독립 복합 traversal budget과 typed diagnostic을 적용한다.

### Drivers

1. graph 생성 단계에서 특정 도메인 의미를 제거해야 한다.
2. 조건, 호출, argument, field, branch 구조를 typed relation으로 보존해야 한다.
3. 넓고 깊은 호출 graph를 예측 가능하게 제한해야 한다.
4. 미지원과 budget truncation을 구분해야 한다.
5. 동일 source snapshot에서 재현 가능한 Evidence ID가 필요하다.

### Alternatives

- **선택: 별도 Fact Graph와 builder**
  - 장점: legacy 호환, 의미 분리, 단계적 migration
  - 단점: 일시적으로 두 graph 유지 비용 발생
- **기각: 기존 GraphNode/Edge 확장**
  - 사유: BUSINESS_RULE 의미와 nullable 필드가 섞이고 기존 소비자 영향이 큼
- **기각: legacy graph를 Fact Graph로 변환**
  - 사유: 이미 손실된 operand/branch/type 사실을 복원할 수 없고 의미 오염이 유지됨

## Final File Plan

### Build

- `build.gradle`: jqwik 1.7.4 test dependency 추가

### Domain

- `analysis/domain/fact/FactNodeType.java`
- `analysis/domain/fact/FactEdgeType.java`
- `analysis/domain/fact/TypeResolutionStatus.java`
- `analysis/domain/fact/DiagnosticSeverity.java`
- `analysis/domain/fact/TraversalDecision.java`
- `analysis/domain/fact/SourceRange.java`
- `analysis/domain/fact/TypeResolution.java`
- `analysis/domain/fact/FactNodePayload.java`
- `analysis/domain/fact/FactNode.java`
- `analysis/domain/fact/FactEdge.java`
- `analysis/domain/fact/FactCodeGraph.java`
- `analysis/domain/fact/FactGraphTraversalBudget.java`
- `analysis/domain/fact/FactGraphTraversalStats.java`
- `analysis/domain/fact/FactGraphDiagnostic.java`
- `analysis/domain/fact/FactGraphBuildResult.java`

### Support

- `analysis/support/fact/DeterministicFactNodeIdGenerator.java`
- `analysis/support/fact/DefaultMethodTraversalPolicy.java`
- `analysis/support/fact/FactGraphAccumulator.java`
- `analysis/support/fact/FactExpressionVisitor.java`
- `analysis/support/fact/FactMethodVisitor.java`
- `analysis/support/fact/DefaultFactCodeGraphBuilder.java`
- `analysis/support/fact/FactGraphIntegrityValidator.java`

### Tests

- `analysis/fact/FactDomainModelTest.java`
- `analysis/fact/FactNodeIdGeneratorProperties.java`
- `analysis/fact/FactCodeGraphBuilderTest.java`
- `analysis/fact/FactTraversalBudgetTest.java`

## Goal Breakdown

- G001: domain model과 invariant 구현
- G002: 결정적 ID, accumulator, integrity validator 구현
- G003: traversal policy와 AST visitor 구현
- G004: builder orchestration, budget, diagnostic 구현
- G005: example-based builder/budget tests 구현
- G006: jqwik properties 구현
- G007: 관련 및 전체 회귀 검증

## Verification

### Unit

- domain invariant와 immutability
- deterministic identity 500 tries 이상
- operand/receiver/argument/branch structure
- type resolution failure 상태
- depth/method/edge budget 및 cycle

### Regression

- Unit 00 baseline test
- 기존 graph builder test
- 전체 `gradlew test`
- `git diff --check`

### Evidence

- goal별 대상 test와 pass count
- PBT tries와 seed 출력
- 전체 suite pass/fail/skip 집계
- 변경 파일과 기존 legacy graph 파일 비변경 확인

## PBT Compliance

- 적용: PBT-01~04, 07~10
- N/A: PBT-05 oracle, PBT-06 stateful
- framework: jqwik 1.7.4

## Cross-Review Trail

| 단계 | 파일 | 결과 |
|:---|:---|:---|
| Planner | `stage-01-planner.md` | PASS |
| Critic | `stage-02-critic.md` | OKAY |
| Architect | `stage-03-architect.md` | APPROVE |
| Reconciliation | `stage-04-reconciliation.md` | PASS |

## 승인 체크리스트

- [x] Functional Design 결정 반영
- [x] NFR Requirements 및 Design 반영
- [x] 모든 수정 파일 열거
- [x] 기각 대안 포함
- [x] example/PBT/회귀 검증 포함
- [x] Evidence 규칙 포함
- [x] intent mapping 100%

