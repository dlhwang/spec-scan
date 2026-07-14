# Unit 02 후보와 해석 모델 최종 개발 및 검증 계획서

## Metadata

- **Unit Slug**: `rule-based-static-analysis-u02`
- **Plan UUID**: `8dd7c860-3a57-440f-a8a4-50857a6b78c5`
- **Date Finalized**: `2026-07-14T13:01:11+09:00`
- **Review Pipeline**: Planner PASS | Critic OKAY | Architect APPROVE | Reconciliation PASS
- **Status**: PENDING USER APPROVAL

## ADR

### Decision

Fact condition별 구조 후보와 Rule 평가별 의미 후보를 분리한 immutable candidate domain을 신규 package에 도입하고, 결정적 ID, typed diagnostic, 상태 및 참조 무결성을 생성 경계에서 검증한다.

### Drivers

1. 구조 추출 실패와 의미 미해석을 구분해야 한다.
2. 의미 성공과 target 미해석을 동시에 표현해야 한다.
3. 특정 class나 method 이름 없이 Fact 구조만으로 후보화해야 한다.
4. 모든 후보가 source evidence로 역추적 가능해야 한다.
5. 동일 입력에서 후보 identity가 재현 가능해야 한다.
6. 기존 output 경로를 깨지 않고 단계적으로 도입해야 한다.

### Alternatives

- **선택: 구조 후보와 의미 후보 분리**
  - 장점: 복수 Rule, 미매칭, 부분 해석을 손실 없이 표현
  - 단점: 두 candidate 유형과 참조 무결성 관리 필요
- **기각: 기존 ValidationCandidate 확장**
  - 사유: 단일 sourceType과 target 중심 모델로 세 상태 축 및 복수 Rule을 안전하게 표현하기 어려움
- **기각: API method별 단일 후보 집계**
  - 사유: 조건별 evidence 경계와 부분 실패가 섞이고 작은 변경에도 identity 변동 범위가 커짐
- **기각: Rule engine까지 동시 구현**
  - 사유: Unit 03 책임을 침범하고 모델 검증과 매칭 결함을 구분하기 어려움

## Final File Plan

### Domain Enums

- `src/main/java/io/atworks/specscan/analysis/domain/candidate/ExtractionStatus.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/SemanticStatus.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/TargetResolutionStatus.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/PredicateType.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/BusinessRuleCategory.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/ConstraintKind.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/EvidenceRole.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/CandidateDiagnosticSeverity.java`

### Domain Records

- `src/main/java/io/atworks/specscan/analysis/domain/candidate/EvidenceRef.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/CandidateDiagnostic.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/NormalizedConstraint.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/PredicateCandidate.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/BusinessRuleCandidate.java`
- `src/main/java/io/atworks/specscan/analysis/domain/candidate/CandidateResolutionResult.java`

### Support

- `src/main/java/io/atworks/specscan/analysis/support/candidate/DeterministicCandidateIdGenerator.java`
- `src/main/java/io/atworks/specscan/analysis/support/candidate/CandidateInvariantValidator.java`
- `src/main/java/io/atworks/specscan/analysis/support/candidate/PredicateTypeClassifier.java`
- `src/main/java/io/atworks/specscan/analysis/support/candidate/EvidenceMapper.java`
- `src/main/java/io/atworks/specscan/analysis/support/candidate/BusinessRuleCandidateFactory.java`
- `src/main/java/io/atworks/specscan/analysis/support/candidate/CandidateResolutionAccumulator.java`
- `src/main/java/io/atworks/specscan/analysis/support/candidate/CandidateResultIntegrityValidator.java`

### Tests

- `src/test/java/io/atworks/specscan/analysis/candidate/CandidateDomainModelTest.java`
- `src/test/java/io/atworks/specscan/analysis/candidate/CandidateSupportTest.java`
- `src/test/java/io/atworks/specscan/analysis/candidate/CandidateIdGeneratorProperties.java`
- `src/test/java/io/atworks/specscan/analysis/candidate/CandidateInvariantProperties.java`

### Existing Files Intentionally Unchanged

- `build.gradle`
- `src/main/java/io/atworks/specscan/analysis/domain/ValidationCandidate.java`
- `src/main/java/io/atworks/specscan/analysis/domain/ApiCondition.java`
- `src/main/java/io/atworks/specscan/analysis/domain/NormalizedResult.java`
- Unit 01 Fact domain 및 builder

## Goal Breakdown

- G001: candidate enum, evidence, diagnostic, constraint domain 구현
- G002: predicate 및 business rule candidate와 지역 invariant 구현
- G003: aggregate, 상태 및 참조 무결성 validator 구현
- G004: 결정적 ID, classifier, evidence mapper와 factory 구현
- G005: accumulator와 부분 실패 diagnostic 구현
- G006: example-based domain/support test 구현
- G007: jqwik identity 및 invariant property 구현
- G008: 관련 및 전체 회귀 검증

## Verification

### Unit

- UNKNOWN과 OTHER 상태 조합
- Rule 미매칭과 NOT_BUSINESS_RULE 구분
- semantic resolved와 target unresolved 조합
- evidence 및 diagnostic 필수 조건
- duplicate candidate와 dangling predicate/evidence reference
- constraint null/empty 및 immutable collection
- predicate ID 500 tries 이상
- 상태 조합 property 200 tries 이상

### Regression

- Unit 00 baseline test
- Unit 01 Fact Graph test와 PBT
- 기존 candidate/output test
- 전체 `gradlew test`
- 작업 범위 `git diff --check`

### Evidence

- goal별 대상 test와 pass count
- PBT tries 및 seed 출력
- 전체 suite pass/fail/skip 집계
- 기존 output model과 Unit 01 Fact 파일 비변경 확인

## PBT Compliance

- 적용: PBT-01~04, 07~10
- N/A: PBT-05 oracle, PBT-06 stateful
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
- [x] Unit 03 및 output migration 범위 제외
- [x] example/PBT/회귀 검증 포함
- [x] Evidence와 compatibility 규칙 포함
- [x] intent mapping 100%
