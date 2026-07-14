# Unit 03 Graph Rule Engine 최종 개발 및 검증 계획서

## Metadata

- **Unit Slug**: `rule-based-static-analysis-u03`
- **Plan UUID**: `244211b8-095e-4b9c-9e4a-cec2ea2cddbe`
- **Date Finalized**: `2026-07-14T13:37:36+09:00`
- **Review Pipeline**: Planner PASS | Critic OKAY | Architect APPROVE | Reconciliation PASS
- **Status**: PENDING USER APPROVAL

## ADR

### Decision

failure-linked condition detector와 독립 `GraphRule` SPI를 도입하고, immutable pack registry, Rule별 격리 실행, Evidence 기반 exact dedup, 비파괴 precedence와 trace-derived report를 갖는 single-thread engine을 구현한다.

### Drivers

1. candidate 탐지와 의미 해석을 분리해야 한다.
2. Rule 추가가 중앙 분기 수정을 요구하면 안 된다.
3. Rule 하나의 실패가 전체 분석을 중단하면 안 된다.
4. 복수 Rule과 복수 Evidence 결과를 손실 없이 보존해야 한다.
5. precedence와 ambiguity가 조용한 후보 삭제를 만들면 안 된다.
6. 미매칭 predicate도 명시적 결과로 남아야 한다.

### Alternatives

- **선택: immutable Rule Pack과 격리 engine**
  - 장점: 확장성, 결정성, failure isolation과 관찰 가능성
  - 단점: registry, report, dedup 및 conflict component 필요
- **기각: 중앙 if-else Rule engine**
  - 사유: Rule 추가 시 core 수정과 특정 소스 특화가 누적됨
- **기각: 최초/최고 confidence 후보만 유지**
  - 사유: 유효한 복수 의미와 Evidence를 잃음
- **기각: 모든 return을 failure로 탐지**
  - 사유: 정상 분기와 조기 반환 false positive가 큼
- **기각: parallel Rule 실행**
  - 사유: 초기 규모에서 필요성이 없고 결정성·통계 검증 복잡도만 증가함

## Required Upstream Corrections

### Unit 01 branch outcome 정확도

- `src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java`
- `src/test/java/io/atworks/specscan/analysis/fact/FactCodeGraphBuilderTest.java`

상위 branch가 nested condition의 outcome을 직접 소유하지 않도록 direct branch statement 추출로 수정하고 회귀 테스트를 추가한다.

### Unit 02 Evidence-aware identity

- `src/main/java/io/atworks/specscan/analysis/support/candidate/DeterministicCandidateIdGenerator.java`
- `src/main/java/io/atworks/specscan/analysis/support/candidate/BusinessRuleCandidateFactory.java`
- `src/test/java/io/atworks/specscan/analysis/candidate/CandidateIdGeneratorProperties.java`
- `src/test/java/io/atworks/specscan/analysis/candidate/CandidateDomainModelTest.java`

기존 ID overload를 유지하고 Evidence fingerprint overload와 factory 경로를 추가한다.

## Final File Plan

### Rule Domain과 SPI

- `src/main/java/io/atworks/specscan/analysis/domain/rule/RuleLayer.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/MethodScope.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/FailureOutcomeDecision.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/ValidationCandidateDetector.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/FailureOutcomePolicy.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/GraphRule.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/RulePrecedence.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/RulePack.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/RuleExecutionDiagnosticSeverity.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/RuleExecutionDiagnostic.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/RuleExecutionReport.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/GraphRuleEngine.java`
- `src/main/java/io/atworks/specscan/analysis/domain/rule/GraphRuleEngineResult.java`

### Rule Support

- `src/main/java/io/atworks/specscan/analysis/support/rule/DefaultValidationCandidateDetector.java`
- `src/main/java/io/atworks/specscan/analysis/support/rule/RulePackRegistry.java`
- `src/main/java/io/atworks/specscan/analysis/support/rule/RuleInvocationBoundary.java`
- `src/main/java/io/atworks/specscan/analysis/support/rule/CandidateMatchKeyFactory.java`
- `src/main/java/io/atworks/specscan/analysis/support/rule/RuleMatchAccumulator.java`
- `src/main/java/io/atworks/specscan/analysis/support/rule/RuleConflictAnnotator.java`
- `src/main/java/io/atworks/specscan/analysis/support/rule/UnresolvedCandidateFactory.java`
- `src/main/java/io/atworks/specscan/analysis/support/rule/RuleExecutionStats.java`
- `src/main/java/io/atworks/specscan/analysis/support/rule/DefaultGraphRuleEngine.java`

### Tests

- `src/test/java/io/atworks/specscan/analysis/rule/ValidationCandidateDetectorTest.java`
- `src/test/java/io/atworks/specscan/analysis/rule/GraphRuleEngineTest.java`
- `src/test/java/io/atworks/specscan/analysis/rule/GraphRuleEngineOrderingProperties.java`
- `src/test/java/io/atworks/specscan/analysis/rule/CandidateDeduplicationProperties.java`
- `src/test/java/io/atworks/specscan/analysis/rule/RuleFailureIsolationProperties.java`
- `src/test/java/io/atworks/specscan/analysis/rule/RuleExecutionReportProperties.java`

### Existing Files Intentionally Unchanged

- `build.gradle`
- 기존 `ValidationEvidenceGraphBuilder` 및 legacy graph domain
- 기존 `ValidationCandidate`, `ApiCondition`, `NormalizedResult`
- 기존 output normalizer와 OAS assembler

## Goal Breakdown

- G001: upstream branch outcome 회귀 수정
- G002: Evidence-aware business candidate identity 확장
- G003: Rule domain, SPI, pack과 report 모델 구현
- G004: conservative candidate detector와 method scope 검증 구현
- G005: immutable registry와 isolated Rule invocation 구현
- G006: exact dedup, conflict, precedence와 unresolved fallback 구현
- G007: default engine과 report orchestration 구현
- G008: detector 및 engine example tests 구현
- G009: ordering, dedup, isolation과 report PBT 구현
- G010: 관련 및 전체 회귀 검증

## Verification

### Unit

- direct throw와 nested branch outcome 구분
- 일반 return 제외 및 policy return 포함
- method scope 참조 검증
- duplicate ruleId 구성 거부
- Rule 0개, 미매칭, 복수 결과, exception, null과 invalid 결과
- Evidence별 동일 Rule 후보 identity 구분
- exact duplicate 제거
- ambiguity 및 non-destructive precedence
- report counter 일치

### Property-Based

- 등록 순서 결과 불변성 200 tries 이상
- duplicate multiplicity dedup 300 tries 이상
- failure injection isolation 200 tries 이상
- report counter invariant 300 tries 이상

### Regression

- Unit 00 baseline
- Unit 01 Fact Graph tests와 PBT
- Unit 02 candidate tests와 PBT
- 기존 candidate/output tests
- 전체 `gradlew test`
- 작업 범위 `git diff --check`

### Evidence

- goal별 test와 pass count
- PBT tries와 reproduction seed
- 전체 suite pass/fail/skip 집계
- 기존 legacy graph와 output 파일 비변경 확인

## PBT Compliance

- 적용: PBT-01~04, 07~10
- N/A: PBT-05 reference oracle, PBT-06 external state machine
- framework: 기존 jqwik 1.7.4 재사용

## Cross-Review Trail

| 단계 | 파일 | 결과 |
|:---|:---|:---|
| Planner | `stage-01-planner.md` | PASS |
| Critic | `stage-02-critic.md` | OKAY |
| Architect | `stage-03-architect.md` | APPROVE |
| Reconciliation | `stage-04-reconciliation.md` | PASS |

## 승인 체크리스트

- [x] Functional Design 결정 반영
- [x] NFR Requirements와 Design 반영
- [x] 모든 수정 파일 열거
- [x] upstream dependency correction과 회귀 test 명시
- [x] 구체 Rule과 output migration 제외
- [x] example/PBT/전체 회귀 검증 포함
- [x] intent mapping 100%
