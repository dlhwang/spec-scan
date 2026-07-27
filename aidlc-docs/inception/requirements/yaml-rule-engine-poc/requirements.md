# YAML Rule Engine PoC — 요구사항

## 1. 의도 분석 요약

- **사용자 요청**: `Semantic Analysis & Rule Engine Layer`의 시맨틱 분류 및 코드그래프 탐색 규칙을 제한된 제네릭 YAML로 외부화할 수 있는지 실제 룰 이관을 통해 검증한다.
- **요청 유형**: 기존 시스템의 신규 기능 및 아키텍처 개선 PoC
- **범위 추정**: Semantic Analysis와 Graph Rule Engine의 다중 컴포넌트
- **복잡도 추정**: 복잡
- **요구사항 깊이**: 종합
- **구현 상태**: 미착수. 본 문서 승인 후에도 Workflow Planning과 후속 설계 승인이 선행되어야 한다.

## 2. 확정 방향

- PoC는 **Phase 2 Go/No-Go 판정**에서 종료한다.
- YAML은 `match`, `follow`, 상태 전이, 경로 한정자, 평가 조합을 선언한다.
- Java는 그래프 인덱스, 순회 스케줄러, 결정론, traversal budget, dispatch precedence, suppression, candidate identity와 evidence 무결성을 유지한다.
- 대표 이관 대상은 `StandardGuard`, `PasswordEncoder`, `OptionalLookup`, `BinaryConstraint`, `CompositeValidation` 5종이다.
- 이관 전후의 외부 관찰 결과는 완전히 같아야 한다.
- `custom_expression`, script, reflection 또는 YAML에서 임의 Java 함수를 호출하는 탈출구는 허용하지 않는다.
- 성능은 측정하고 보고하지만 이번 PoC의 합격 기준으로 사용하지 않는다.
- Security Baseline과 Property-Based Testing 확장은 이번 PoC에 적용하지 않는다.

## 3. 범위

### 3.1 포함 범위

1. 기존 Rule Engine 결과의 Golden Corpus 기준선 확보
2. YAML schema version과 엄격한 구조 검증
3. 제한된 선언형 primitive 및 combinator 실행 모델
4. 기존 Java 경로와 YAML 경로의 병행 실행 및 결과 비교
5. 대표 룰 5종의 단계적 이관 실습
6. 표현 불가 사례의 기록과 분류
7. Phase 2 종료 시 정량적 Go/Partial/No-Go 판정

### 3.2 제외 범위

- `FactCodeGraph` 생성 규칙과 JavaParser AST 방문 로직
- 그래프 저장 모델과 인덱스 구조의 YAML 정의
- dispatch precedence와 suppression chain의 YAML 외부화
- 임의 표현식, 범용 반복문, 재귀 함수, 사용자 스크립트
- Phase 3 실행 트레이서와 전체 룰 작성 가이드
- Phase 4 rule registry 버저닝, CI Gate, hot-reload
- 프로덕션 배포 및 운영 전환

## 4. 단계별 검증 흐름

1. **Phase 0 — 안전망**: 현재 Java 엔진의 기대 결과와 YAML validation 계약을 고정한다.
2. **Phase 1 — 최소 실행 모델**: 이관 대상에서 역산한 primitive 후보로 제한된 실행기를 구성한다.
3. **Phase 2 — 실제 이관**: 난이도가 다른 5개 룰을 하나씩 이관하고 매번 결과를 비교한다.
4. **Decision Gate**: 표현 가능 비율, 탈출구 요구 빈도, 완전 동등성 결과와 성능 측정치를 근거로 결론을 낸다.

## 5. 기능 요구사항

## Requirement R-YAML-001: Golden Corpus 기준선

### Description

PoC 변경 전 Java 엔진의 외부 관찰 결과를 Golden Corpus 기준선으로 고정해야 한다.

### Acceptance Criteria

- 기존 평가 fixture와 실제 탐지 이력이 있는 후보를 이용해 5~10개의 corpus 단위를 선정한다.
- 각 corpus에 rule ID, category, normalized constraint, target, evidence, diagnostic, suppression 결과를 포함한 결정론적 snapshot이 존재한다.
- 동일 입력을 반복 실행했을 때 snapshot이 동일하다.
- 기준선 생성 시 사용한 엔진 revision과 입력 revision을 기록한다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: integration, snapshot
- **Required Test Evidence**: 동일 corpus 반복 실행 결과와 baseline snapshot diff 0건
- **Manual Verification Rationale**: corpus 대표성과 기존 True/False Positive 이력은 수동 검토가 필요함
- **Goal-Driven Evidence Tracking**: `G-YAML-P0-01`

## Requirement R-YAML-002: YAML 스키마와 진단

### Description

기존 `YamlRulePackLoader`를 확장하여 버전이 명시된 제한형 YAML 문법을 엄격히 검증해야 한다.

### Acceptance Criteria

- schema version, rule ID, primitive 종류, 필수 필드와 허용 enum이 명시된다.
- unknown field, duplicate rule ID, 누락 필드, 잘못된 enum, 지원하지 않는 primitive를 거부한다.
- 오류에는 가능한 한 rule ID와 YAML 위치가 포함된다.
- validation 실패 시 부분 룰팩을 실행하지 않는다.
- 기존 YAML 형식의 호환 또는 명시적 migration 정책이 문서화된다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: unit, integration
- **Required Test Evidence**: 정상/오류 YAML fixture별 진단 코드, rule ID와 위치 assertion
- **Manual Verification Rationale**: N/A
- **Goal-Driven Evidence Tracking**: `G-YAML-P0-02`

## Requirement R-YAML-003: 제한된 선언형 Primitive 모델

### Description

YAML은 범용 프로그래밍 언어가 아니라 그래프 패턴과 상태 평가를 조합하는 유한한 primitive 집합을 제공해야 한다.

### Acceptance Criteria

- 초기 후보는 `match`, `follow`, 상태 전이, 경로 한정자, `all_of`/`any_of`/`not` 평가 조합을 포함한다.
- primitive 입력과 출력 타입, 허용 edge/node 종류, 방향과 종료 조건이 스키마에 정의된다.
- 모든 순회에는 Java 런타임이 강제하는 depth/node/time budget이 존재한다.
- 동일 graph, rule pack과 budget은 동일 결과와 동일한 정렬 순서를 만든다.
- primitive 최종 집합은 사전 고정하지 않고 대표 룰 이관에서 반복적으로 나타난 요구만 승격한다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: unit, integration
- **Required Test Evidence**: primitive별 정상·경계·budget 종료 예제 테스트와 결정론 반복 실행
- **Manual Verification Rationale**: primitive 일반성 승격 판단은 설계 리뷰가 필요함
- **Goal-Driven Evidence Tracking**: `G-YAML-P1-01`

## Requirement R-YAML-004: Java 런타임 불변식 보존

### Description

엔진 안전성, 결정론과 결과 계약을 지키는 책임은 YAML이 아니라 Java 런타임에 남아야 한다.

### Acceptance Criteria

- `FactGraphIndex` 및 그래프 인덱싱은 YAML로 외부화하지 않는다.
- traversal scheduling, cycle detection과 budget 집행은 Java가 담당한다.
- `RulePackRegistry`의 ID 유일성 및 precedence 계약을 보존한다.
- `SemanticRuleDispatcher`의 dispatch precedence와 suppression 의미를 YAML이 변경할 수 없다.
- candidate identity, evidence mapping, conflict annotation과 unresolved fallback 계약을 보존한다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: unit, integration, regression
- **Required Test Evidence**: 기존 Rule Engine 계약 테스트와 migrated YAML 경로의 동일 assertion 결과
- **Manual Verification Rationale**: 책임 경계 위반 여부는 architecture review가 필요함
- **Goal-Driven Evidence Tracking**: `G-YAML-P1-02`

## Requirement R-YAML-005: 병행 실행과 완전 동등성 비교

### Description

각 룰은 기존 Java 구현과 YAML 구현을 동일 입력에서 병행 실행해 외부 관찰 결과를 비교할 수 있어야 한다.

### Acceptance Criteria

- 비교 단위는 rule ID, category, normalized constraint, target, evidence, diagnostic, suppression 결과다.
- 신규 내부 trace 또는 계측 데이터만 비교 대상에서 제외한다.
- 순서 차이로 회귀를 숨기지 않도록 비교 전 정렬 계약이 명시된다.
- 차이가 발생하면 신규 탐지, 소실 탐지, 필드 변경으로 분류된 diff를 제공한다.
- 차이가 있는 룰은 이관 완료로 표시하지 않는다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: integration, regression, snapshot
- **Required Test Evidence**: 각 이관 룰과 전체 Golden Corpus에서 diff 0건
- **Manual Verification Rationale**: N/A
- **Goal-Driven Evidence Tracking**: `G-YAML-P1-03`

## Requirement R-YAML-006: 대표 룰 5종 이관

### Description

표현력 검증을 위해 서로 다른 알고리즘 특성을 가진 5종의 기존 룰을 YAML 실행 모델로 이관한다.

### Acceptance Criteria

- `StandardGuard`는 signature lookup, argument role과 normalized operator를 검증한다.
- `PasswordEncoder`는 다중 hop 탐색, polarity와 input origin resolution을 검증한다.
- `OptionalLookup`은 lookup chain 분류와 suppression 의미를 검증한다.
- `BinaryConstraint`는 operand, operator, literal과 target resolution을 검증한다.
- `CompositeValidation`은 다중 조건, activation guard와 복수 candidate 생성을 검증한다.
- 각 이관 기록에는 필요한 primitive, 막힌 지점, 추가 primitive 후보와 동등성 결과를 남긴다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: unit, integration, regression
- **Required Test Evidence**: 룰별 YAML fixture, 기존 Java 결과와의 Golden Corpus diff, 표현력 기록
- **Manual Verification Rationale**: 표현 불가 원인의 분류는 설계 리뷰가 필요함
- **Goal-Driven Evidence Tracking**: `G-YAML-P2-01` ~ `G-YAML-P2-05`

## Requirement R-YAML-007: 탈출구 금지와 표현 불가 백로그

### Description

표현력이 부족한 사례를 임의 코드 실행으로 우회하지 않고 구조화된 설계 입력으로 보존해야 한다.

### Acceptance Criteria

- `custom_expression`, inline script, reflection과 임의 Java callback을 스키마에서 허용하지 않는다.
- 표현 불가 사례에는 대상 룰, 실패한 표현, 필요한 의미, 기존 코드 위치와 빈도를 기록한다.
- 단발성 사례는 primitive로 즉시 추가하지 않는다.
- 반복적이고 일반화 가능한 패턴만 architecture review를 거쳐 신규 primitive 또는 kind 후보로 승격한다.
- 설계상 YAML 외부화가 부적절한 사례는 Java 유지 대상으로 명시한다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: schema validation, unit
- **Required Test Evidence**: 금지 필드와 실행 확장 입력이 거부되는 negative test
- **Manual Verification Rationale**: 승격 또는 Java 유지 분류는 architecture review가 필요함
- **Goal-Driven Evidence Tracking**: `G-YAML-P2-06`

## Requirement R-YAML-008: Go/Partial/No-Go 판정

### Description

Phase 2 종료 시 결과를 정량 기준으로 판정하고 후속 투자를 결정해야 한다.

### Acceptance Criteria

- **Go**: 대표 5개 중 4개 이상이 탈출구 없이 표현되고, 이관된 모든 룰의 Golden Corpus diff가 0건이다.
- **Partial**: 특정 카테고리에서만 재사용 가능한 primitive 조합이 성립하여 Step 3 적용 범위를 제한할 근거가 있다.
- **No-Go**: 이관마다 임의 확장이 필요하거나 Java 런타임 불변식을 YAML로 옮겨야만 표현 가능하다.
- 판정 문서에는 룰별 결과, primitive 재사용률, 표현 불가 목록, 회귀 결과와 성능 측정값이 포함된다.
- Go 판정 전에는 Phase 3 또는 Phase 4에 착수하지 않는다.

### Verification Expectations

- **Automation Required**: Partial
- **Expected Test Level**: integration, regression, benchmark
- **Required Test Evidence**: 룰별 이관 결과와 Golden Corpus diff report, 실행 시간 및 peak memory 측정 report
- **Manual Verification Rationale**: 최종 Go/Partial/No-Go 결정에는 architecture review와 사용자 승인이 필요함
- **Goal-Driven Evidence Tracking**: `G-YAML-GATE-01`

## 6. 비기능 요구사항

- **NFR-YAML-001 결정론**: 동일 입력, 룰팩과 budget은 동일한 candidate 및 evidence 순서를 생성해야 한다.
- **NFR-YAML-002 종료 보장**: 모든 그래프 순회는 cycle detection과 강제 budget을 적용해야 한다.
- **NFR-YAML-003 호환성**: 이관된 룰의 외부 관찰 결과는 기존 Java 결과와 완전히 같아야 한다.
- **NFR-YAML-004 진단 가능성**: schema와 실행 오류는 rule ID, 단계와 가능한 경우 YAML 위치를 제공해야 한다.
- **NFR-YAML-005 보안 경계**: YAML은 코드 실행, 파일/네트워크 접근 또는 reflection을 유발할 수 없다.
- **NFR-YAML-006 성능 관찰**: 평가 시간과 peak memory를 동일 corpus에서 측정하되 합격/실패 기준으로 사용하지 않는다.
- **NFR-YAML-007 유지보수성**: primitive는 단일 룰 전용 연산이 아니라 반복되는 의미 단위일 때만 추가한다.
- **NFR-YAML-008 테스트 전략**: 예제 기반 unit, integration, regression과 snapshot 테스트를 사용하며 PBT 확장은 적용하지 않는다.

## 7. 체감 가능한 PoC 데모

### 데모 A: Guard API 추가

기존 Java classifier를 변경하지 않고 YAML에 owner/signature, argument role과 normalized operator를 추가하여 새로운 guard API가 동일한 `NOT_NULL` 결과를 생성함을 보여준다.

### 데모 B: Password 검증 탐색 변경

Java의 BFS 구현을 직접 수정하지 않고 YAML에서 허용 edge 집합과 call depth를 변경한 룰 variant를 실행한다. Java 런타임은 cycle과 budget을 통제하며 결과 diff를 출력한다.

### 데모 C: 표현 불가 사례

`CompositeValidation` 이관 중 primitive로 표현되지 않는 의미가 발견되면 script로 우회하지 않고 표현 불가 백로그와 Go/Partial/No-Go 판단 근거로 남긴다.

## 8. 성공 및 실패 기준

- **성공**: 5개 중 4개 이상을 임의 탈출구 없이 표현하고, 이관한 모든 룰에서 외부 결과 diff 0건
- **부분 성공**: 특정 룰 카테고리에 한정해 재사용 가능한 YAML 외부화 경계를 증명
- **실패**: 대부분의 룰이 전용 primitive 또는 임의 표현식을 요구하거나 Java 런타임 불변식 이동 없이는 동등성을 달성할 수 없음

## 9. 확장 설정

| Extension | Enabled | Decided At |
| :--- | :--- | :--- |
| Security Baseline | No | Requirements Analysis |
| Property-Based Testing | No | Requirements Analysis |

## 10. 추적 문서

- 개념 로드맵: `C:/Users/Administrator/.gemini/antigravity/brain/5c50ba27-5e34-4f75-a609-1db609985657/yaml_rule_engine_poc_roadmap.md`
- 요구사항 답변: `aidlc-docs/inception/requirements/yaml-rule-engine-poc/requirement-verification-questions.md`
- 영속 결정: `aidlc-docs/inception/requirements/yaml-rule-engine-poc/specs/deep-interview-yaml-externalization-boundary.md`
- INCEPTION 계획: `aidlc-docs/inception/plans/yaml-rule-engine-poc-inception-plan.md`

