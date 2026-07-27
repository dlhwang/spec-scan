# YAML Rule Engine PoC — Unit of Work Requirement Map

> Workflow Planning에서 User Stories를 생략했으므로, 승인된 Requirements와 Acceptance Criteria를 story 대체 추적 단위로 사용한다.

## 1. Capability 흐름

```text
안전한 일반화 기준을 세운다                  U01
  -> 선언 입력을 안전하게 compile한다        U02
  -> graph facts를 typed primitive로 실행한다 U03
  -> core semantic family를 외부화한다        U04
  -> framework/legacy 예외를 격리·교정한다    U05
  -> 여러 repository에서 증명하고 판정한다    U06
```

## 2. Functional Requirement Map

| Requirement | Capability/Acceptance 요약 | Primary Unit | Supporting Units | 필수 Evidence |
| :--- | :--- | :--- | :--- | :--- |
| R2-YAML-001 Spring MVC 지원 경계 | 이름 allowlist 없이 endpoint/graph 분석 시도, unsupported 명시 | U03 | U01, U06 | cross-domain endpoint/graph integration, unsupported diagnostic |
| R2-YAML-002 Traversal Configuration | evaluation config `15/500/10000`, 양수 검증, truncation, 정상 scan 격리 | U02 | U03, U06 | default/override/invalid tests, each-budget partial, normal-scan isolation |
| R2-YAML-003 Typed Primitive Registry | stable ID/type/category, unknown/type mismatch 거부, follow 종료 | U03 | U02 | registry contract, compile negative, cycle/budget tests |
| R2-YAML-004 Runtime Binding | graph에서 target/literal/operator/evidence 생성, no guess | U03 | U04, U05 | rename/metamorphic target binding, unresolved negative |
| R2-YAML-005 Core Recipe Family | binary/composite/null-empty/Optional 4 family 재사용 | U04 | U01, U03, U06 | family별 2+ corpus/variant integration |
| R2-YAML-006 Optional Framework Packs | Spring Data/Security/JPA evidence 기반 활성화 | U05 | U02, U03, U06 | presence/absence, unrelated-name, type-failure negative |
| R2-YAML-007 Delegated Guard | hardcoding 제거, evidence 없으면 unresolved 또는 기본 pack 제외 | U05 | U01, U06 | no-guess regression, corrected `REPLACE` diff |
| R2-YAML-008 미분류/불완전성 | unclassified/unresolved/partial과 원인/evidence 보고 | U03 | U05, U06 | status completeness, budget/type/unknown fixture |
| R2-YAML-009 Cross-Repository Corpus | 5~10 corpus, 6~10 endpoint 수준, rename holdout, coverage 분리 | U01 | U04~U06 | corpus manifest, graph/semantic coverage, holdout report |
| R2-YAML-010 회귀와 교정 분리 | preserve/replace/unsupported별 판정 | U01 | U05, U06 | disposition completeness, snapshot/corrected diagnostic report |

## 3. Functional Acceptance 상세 배정

### R2-YAML-001

| Acceptance Contract | Owner | Verification point |
| :--- | :--- | :--- |
| project-specific controller/package/field allowlist 없음 | U03 | registry/primitive forbidden-dependency review |
| supported Spring MVC endpoint 발견 | U03 | endpoint integration fixture |
| endpoint별 reachable subgraph | U03 | evaluation graph preparation integration |
| root/source 실패 diagnostic | U03 | unsupported graph fixture |
| repository 전체 실패 대신 scope별 unsupported | U06 | report aggregation test |

### R2-YAML-002

| Acceptance Contract | Owner | Verification point |
| :--- | :--- | :--- |
| positive integer validation | U02 | schema parameterized test |
| default `15/500/10000` | U02 | config default test |
| evaluation 시작 시 immutable snapshot | U02 | plan immutability/digest test |
| budget 초과 `PARTIAL_ANALYSIS` | U03 | depth/method/edge limit tests |
| invalid config는 evaluation만 거부 | U02 | normal-scan isolation integration |

### R2-YAML-003

| Acceptance Contract | Owner | Verification point |
| :--- | :--- | :--- |
| stable ID와 typed input/output | U03 | descriptor contract test |
| 7개 primitive category | U03 | registry completeness test |
| unknown/type mismatch load 거부 | U02 | compiler negative test |
| 모든 follow에 cycle/budget | U03 | termination test |
| repository-specific 전제 없음 | U03 | dependency/constant review |

### R2-YAML-004

| Acceptance Contract | Owner | Verification point |
| :--- | :--- | :--- |
| concrete field/literal recipe 상수 금지 | U04 | YAML forbidden-constant review |
| origin/value-flow target resolution | U03 | binding integration test |
| literal/enum node에서 expected binding | U03 | literal/enum fixture |
| failure polarity로 operator 계산 | U04 | binary polarity matrix test |
| 증명 실패 시 `UNRESOLVED` | U03 | negative/no-guess test |

### R2-YAML-005

| Acceptance Contract | Owner | Verification point |
| :--- | :--- | :--- |
| binary failure guard | U04 | binary family test |
| composite/enum/복수 requirement | U04 | composite family test |
| standard null/empty guard | U04 | standard guard family test |
| JDK Optional failure | U04 | Optional family test |
| family별 2+ repository/fixture | U04 | U01 corpus 기반 cross-domain test |

### R2-YAML-006

| Acceptance Contract | Owner | Verification point |
| :--- | :--- | :--- |
| Spring Data repository lookup | U05 | resolved receiver/Optional chain test |
| Spring Security PasswordEncoder | U05 | resolved type/signature test |
| JPA `@Version` | U05 | annotation evidence test |
| method name 단독 의미 확정 금지 | U05 | unrelated call negative test |
| evidence 없는 repository에서 candidate 0 | U05 | pack isolation test |

### R2-YAML-007

| Acceptance Contract | Owner | Verification point |
| :--- | :--- | :--- |
| aggregate prefix hardcoding 없음 | U05 | source/YAML constant review |
| 업무 target/operator 합성 없음 | U05 | no-guess negative regression |
| argument→parameter→return→origin evidence | U05 | resolved delegated fixture 또는 explicit unsupported 기록 |
| 증명 실패 unresolved | U05 | `UNRESOLVED_DELEGATED_GUARD` test |
| 일반화 불가 시 evaluation default pack 제외 | U05 | pack registry test |

### R2-YAML-008

| Acceptance Contract | Owner | Verification point |
| :--- | :--- | :--- |
| unknown failure candidate `UNCLASSIFIED` | U03 | unknown custom guard test |
| target/origin 실패 `UNRESOLVED` | U03 | unresolved receiver test |
| graph/budget 누락 `PARTIAL_ANALYSIS` | U03 | truncation tests |
| repository/endpoint/node/primitive/reason 포함 | U06 | report schema test |
| 불완전 결과를 성공으로 숨기지 않음 | U06 | fallback/raw-status preservation test |

### R2-YAML-009

| Acceptance Contract | Owner | Verification point |
| :--- | :--- | :--- |
| 5~10 corpus | U01 | manifest completeness review |
| repository별 6~10 endpoint 수준 | U01 | scope inventory |
| 다양한 이름/구조 | U01 | corpus diversity rubric |
| rename/mutation holdout | U01 | holdout isolation check |
| graph/semantic coverage 분리 | U06 | report aggregation test |
| holdout 이름 tuning 금지 | U06 | plan/source review |

### R2-YAML-010

| Acceptance Contract | Owner | Verification point |
| :--- | :--- | :--- |
| 모든 baseline에 disposition | U01 | manifest completeness test |
| `PRESERVE` diff 0 | U06 | snapshot diff |
| `REPLACE` corrected expectation | U05 | delegated correction; U06 verdict |
| `UNSUPPORTED` 명시적 diagnostic | U03/U05 | U06 report check |
| 내부 trace/performance만 equivalence 제외 | U06 | canonical comparison test |

## 4. NFR Map

| NFR | 내용 | Primary Unit | Supporting Units | Evidence |
| :--- | :--- | :--- | :--- | :--- |
| NFR-R2-001 결정론 | 같은 source/graph/plan은 같은 결과와 정렬 | U03 | U02, U06 | plan digest, repeated execution/report comparison |
| NFR-R2-002 종료 보장 | cycle detection과 finite budget | U03 | U06 | cyclic graph, depth/method/edge/primitive limit tests |
| NFR-R2-003 무추측 | evidence 없는 target/operator/meaning 금지 | U03 | U04, U05, U06 | unresolved/no-guess negative suite |
| NFR-R2-004 저장소 독립성 | project/package/field 이름 금지 | U04 | U01, U03, U05, U06 | rename holdout, forbidden-constant review |
| NFR-R2-005 진단 가능성 | unsupported/unclassified/unresolved/partial 구분 | U03 | U05, U06 | status matrix와 report schema test |
| NFR-R2-006 스키마 안전성 | unknown/duplicate/type/expression 거부 | U02 | U06 | strict schema negative suite |
| NFR-R2-007 성능 관찰 | time/memory/graph/primitive count 측정 | U06 | U03 | metrics instrumentation/report |
| NFR-R2-008 테스트 전략 | example/integration/snapshot/rename/holdout | U01 | U02~U06 | level별 evidence manifest |
| NFR-R2-009 보안 경계 | code execution/I/O/reflection 금지 | U02 | U03, U06 | forbidden schema and registry sandbox tests |

## 5. Unit별 Requirement Coverage

| Unit | Primary Functional | Supporting Functional | Primary NFR | Supporting NFR |
| :--- | :--- | :--- | :--- | :--- |
| U01 | 009, 010 | 001, 005~008 | 004, 008 | 001, 003, 005 |
| U02 | 002 | 003, 006 | 006, 009 | 001, 008 |
| U03 | 001, 003, 004, 008 | 002, 005~007 | 001, 002, 003, 005 | 004, 007~009 |
| U04 | 005 | 004, 009, 010 | 004 | 001~003, 005, 008~009 |
| U05 | 006, 007 | 004, 008, 010 | 003, 004 | 001~002, 005, 008~009 |
| U06 | 009, 010 통합 판정 | 001~008 | 007 및 전체 통합 | 001~006, 008~009 |

## 6. Coverage Completeness Check

### Functional

- [x] R2-YAML-001 배정
- [x] R2-YAML-002 배정
- [x] R2-YAML-003 배정
- [x] R2-YAML-004 배정
- [x] R2-YAML-005 배정
- [x] R2-YAML-006 배정
- [x] R2-YAML-007 배정
- [x] R2-YAML-008 배정
- [x] R2-YAML-009 배정
- [x] R2-YAML-010 배정

### Non-functional

- [x] NFR-R2-001 배정
- [x] NFR-R2-002 배정
- [x] NFR-R2-003 배정
- [x] NFR-R2-004 배정
- [x] NFR-R2-005 배정
- [x] NFR-R2-006 배정
- [x] NFR-R2-007 배정
- [x] NFR-R2-008 배정
- [x] NFR-R2-009 배정

## 7. 누락 및 중복 판정

- 미배정 Functional Requirement: 0
- 미배정 NFR: 0
- Primary owner 없는 requirement: 0
- 다중 Primary owner가 있는 항목: R2-YAML-009/010은 U01이 기준을 소유하고 U06이 통합 판정을 소유하도록 단계 책임을 명시함
- User Story artifact 부재: 승인된 선택에 따라 Requirements/Acceptance Criteria mapping으로 대체됨

## 8. Construction Traceability Rule

각 Unit의 Functional Design, NFR Requirements와 NFR Design은 이 문서의 primary/supporting assignment를 그대로 상속한다. 구현 중 requirement owner를 변경하려면 Unit dependency와 story map을 먼저 재승인해야 한다.
