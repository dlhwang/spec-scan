# Stage 3: Architect — Unit 01 Fact Code Graph

## Architecture Audit

### A1. Legacy Compatibility

- **Result**: PASS
- 기존 `ValidationEvidenceGraph`와 builder를 수정하지 않고 신규 graph를 병렬 도입한다.

### A2. Domain Isolation

- **Result**: PASS
- `analysis.domain.fact`는 JavaParser, legacy graph, output model에 의존하지 않는다.

### A3. Dependency Direction

- **Result**: PASS
- JavaParser 의존은 `analysis.support.fact` visitor/builder에 한정되고 support가 domain을 사용한다.

### A4. Semantic Separation

- **Result**: PASS
- node/payload/edge에는 관찰 사실만 존재하며 비즈니스 category는 후속 Rule Unit로 유예된다.

### A5. Scalability and Failure Isolation

- **Result**: PASS
- API별 context와 3차원 budget, diagnostic을 사용한다.

### A6. Testability

- **Result**: PASS
- domain/ID/accumulator는 parser 없이 테스트하고 builder는 최소 source fixture로 테스트할 수 있다.

## Decision

- **Verdict**: APPROVE
- **Rationale**: 신규 bounded context가 legacy 경로와 분리되고 Functional/NFR 결정이 파일 및 검증 계획에 추적된다.
- **Conditions**:
  - source snapshot 간 영속 ID를 보장한다고 문서화하지 않는다.
  - edge budget 추가는 atomic relation 단위로 처리한다.
  - `TypeResolver` 재사용 시 resolved 결과와 source declaration만 소비한다.
  - 신규 graph에 `BUSINESS_RULE` 또는 도메인 고정 문자열을 넣지 않는다.

