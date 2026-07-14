# Stage 2: Critic — Unit 01 Fact Code Graph

## Critique Items

### C1. Unit 범위가 한 번에 과도할 위험

- **Target**: File Plan 13~19
- **Problem**: domain, visitor, traversal, diagnostics를 한 번에 구현하면 실패 원인 격리가 어려울 수 있다.
- **Requested Action**: domain → accumulator → visitor → builder 순서로 각각 테스트를 통과한 뒤 다음 목표로 이동한다.
- **Severity**: HIGH
- **Resolution**: 구현 순서와 Goal 분해 조건에 반영됨.

### C2. `FactGraphTypes.java` 표현의 Java 제약

- **Target**: File Plan 2
- **Problem**: 여러 public enum을 단일 파일에 둘 수 없다.
- **Requested Action**: 계획에 열거한 다섯 enum을 개별 파일로 생성한다.
- **Severity**: MEDIUM
- **Resolution**: File Plan 설명에 개별 파일 생성을 명시함.

### C3. source coordinate 기반 ID의 수정 민감성

- **Target**: ID generator
- **Problem**: 줄 이동 시 ID가 바뀌며 repository revision 간 영속 ID로 오해할 수 있다.
- **Requested Action**: 동일 source snapshot 내 결정성만 계약하고 revision 간 안정성을 주장하지 않는다.
- **Severity**: MEDIUM
- **Resolution**: verification을 동일 입력 반복 실행으로 제한함.

### C4. edge budget의 dangling node 위험

- **Target**: Accumulator
- **Problem**: node 추가 후 edge 한도에 걸리면 연결되지 않은 구조 node가 남을 수 있다.
- **Requested Action**: 구조 관계 추가를 atomic operation으로 제공하고 테스트한다.
- **Severity**: HIGH
- **Resolution**: accumulator 책임과 budget test에 반영됨.

### C5. 기존 TypeResolver 결합 위험

- **Target**: Builder
- **Problem**: 기존 resolver의 graph-specific 동작을 무분별하게 재사용하면 legacy 의미가 유입될 수 있다.
- **Requested Action**: class/method source resolution API만 사용하고 신규 payload 생성은 visitor가 담당한다.
- **Severity**: MEDIUM
- **Resolution**: builder 조건으로 반영됨.

## Verdict

- **Decision**: OKAY
- **Rationale**: 신규 패키지 격리, 단계별 구현, atomic accumulator, legacy graph 비의존 조건으로 주요 위험이 통제된다.

## Residual Risks

| ID | 위험 | 가능성 | 영향 | 완화책 |
|:---|:---|:---|:---|:---|
| R1 | JavaParser resolution 편차 | 중간 | 중간 | unresolved 상태와 fixture 제공 |
| R2 | visitor가 일부 AST 형태 누락 | 중간 | 중간 | 지원 범위 test와 diagnostic |
| R3 | jqwik dependency conflict | 낮음 | 중간 | dependency insight와 전체 test |
| R4 | 신규 파일 수 증가 | 높음 | 낮음 | domain/support package 책임 분리 |

