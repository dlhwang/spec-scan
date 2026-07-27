# Deep Interview Specification: YAML Rule Engine 외부화 경계

> **문서 유형**: 요구사항 인터뷰 영속 사양
> **상태**: Requirements Analysis 승인 대기
> **관련 문서**: `../requirements.md`

## 의사결정 사항 (Decisions)

1. **PoC 종료 범위**: Phase 2 Go/No-Go 판정에서 종료한다.
   - 선택 옵션: Option A
   - 선택 사유: YAML 표현력과 회귀 안전성이라는 핵심 가설을 먼저 검증하고 Phase 3~4의 선행 투자를 피한다.

2. **YAML과 Java의 책임 경계**: 제한된 선언형 primitive는 YAML에 두고 런타임 불변식은 Java에 유지한다.
   - 선택 옵션: Option A
   - 선택 사유: 알고리즘 조합의 변경성을 확보하면서 결정론, 종료 보장과 기존 결과 계약을 보존한다.

3. **대표 이관 포트폴리오**: `StandardGuard`, `PasswordEncoder`, `OptionalLookup`, `BinaryConstraint`, `CompositeValidation` 5종을 사용한다.
   - 선택 옵션: Option A
   - 선택 사유: 단순 매핑, 다중 hop, polarity, lookup chain, target resolution과 복합 조건을 고르게 검증한다.

4. **동등성 기준**: 외부 관찰 결과의 완전 동등성을 요구한다.
   - 선택 옵션: Option A
   - 선택 사유: 탐지 결과뿐 아니라 evidence와 suppression 회귀까지 차단한다.

5. **표현 불가 탈출구**: 임의 표현식을 금지하고 반복 패턴만 primitive 또는 kind 후보로 승격한다.
   - 선택 옵션: Option A
   - 선택 사유: YAML이 범용 언어나 그래프 질의 DSL로 팽창하는 것을 방지한다.

6. **성능 기준**: 성능을 측정하되 이번 PoC의 합격 기준으로 사용하지 않는다.
   - 선택 옵션: Option C
   - 선택 사유: Phase 2에서는 표현 가능성과 완전 동등성을 우선 검증한다.

7. **Security Baseline**: 이번 PoC에는 적용하지 않는다.
   - 선택 옵션: Option B
   - 선택 사유: 실험 범위를 YAML 표현력과 회귀 안전성으로 제한한다.

8. **Property-Based Testing**: 이번 PoC에는 적용하지 않는다.
   - 선택 옵션: Option C
   - 선택 사유: 예제 기반 unit, integration, regression과 snapshot 검증으로 범위를 제한한다.

## 하드 제약 조건 (Constraints)

1. `FactCodeGraph` 생성 및 JavaParser AST 방문 알고리즘은 YAML 외부화 범위에 포함하지 않는다.
2. 그래프 인덱싱, 순회 스케줄링, cycle detection과 traversal budget 집행은 Java가 담당한다.
3. dispatch precedence, suppression, candidate identity, evidence 무결성, conflict annotation과 unresolved fallback은 Java 계약으로 유지한다.
4. `custom_expression`, inline script, reflection과 YAML에서 임의 Java callback 호출을 허용하지 않는다.
5. 모든 YAML 순회는 Java 런타임이 강제하는 유한 budget과 결정론적 정렬을 사용해야 한다.
6. 이관 완료로 인정되는 모든 룰은 Golden Corpus에서 외부 관찰 결과 diff가 0건이어야 한다.
7. 표현 불가 사례는 우회하지 않고 백로그에 기록하며, 반복성과 일반성이 검증된 의미만 신규 primitive 또는 kind 후보가 될 수 있다.
8. Phase 2 Go 판정과 사용자 승인 전에는 Phase 3 트레이서 또는 Phase 4 hot-reload에 착수하지 않는다.
9. 성능 측정 결과는 반드시 보고하되 이번 PoC의 Go/No-Go 합격선으로 사용하지 않는다.
10. Requirements Analysis, Workflow Planning과 후속 설계 승인 전에는 구현을 시작하지 않는다.

## 관련 요구사항

- `R-YAML-001` ~ `R-YAML-008`
- `NFR-YAML-001` ~ `NFR-YAML-008`

## 관련 컨텍스트 (Related Context)

| 참조 유형 | 경로 | 설명 |
| :--- | :--- | :--- |
| 개념 로드맵 | `C:/Users/Administrator/.gemini/antigravity/brain/5c50ba27-5e34-4f75-a609-1db609985657/yaml_rule_engine_poc_roadmap.md` | Step 1~4 외부화 개념과 체감 예시 |
| 요구사항 답변 | `../requirement-verification-questions.md` | 8개 설계 선택의 사용자 답변 |
| 기존 YAML 로더 | `src/main/java/io/atworks/specscan/analysis/support/rule/yaml/YamlRulePackLoader.java` | 현재 method/signature 중심 YAML 경계 |
| Semantic Dispatcher | `src/main/java/io/atworks/specscan/analysis/support/semantic/SemanticRuleDispatcher.java` | Java에 유지할 dispatch와 suppression 경계 |

## 인터뷰 메타데이터

| 항목 | 값 |
| :--- | :--- |
| Slug | `yaml-externalization-boundary` |
| 인터뷰 확정 일시 | `2026-07-22T09:34:39.2724648+09:00` |
| 참여자 | 사용자, Codex |
| 관련 Goal ID | `G-YAML-P0-01` ~ `G-YAML-GATE-01` |
| 선행 문서 | `../requirements.md` |

## 작성 완료 체크리스트

- [x] 모든 의사결정에 선택 옵션과 사유가 기록됨
- [x] 하드 제약 조건이 구현 및 검토 가능한 수준으로 구체화됨
- [x] 관련 컨텍스트가 기록됨
- [x] `specs/` 디렉터리에 저장됨

