# YAML Rule Engine PoC — Application Design 승인

> **상태**: 승인 완료 (2026-07-22)
> **설계 산출물**: `../application-design/yaml-rule-engine-poc/`

## 검토 요약

- 신규 경계: `analysis.domain.recipe`와 `analysis.support.recipe`
- 로딩 방식: 명시적 PoC evaluation 시작 시 세 YAML을 strict validation 후 immutable plan으로 compile-once
- 정상 scan: 기존 Java engine 및 production output 유지
- 실패 격리: YAML/bootstrap/evaluation graph 실패는 evaluation만 중단하며 정상 scan에는 영향 없음
- graph 분리: 정상 scan은 default-budget graph, evaluation은 YAML-derived-budget graph 사용
- PoC evaluation: YAML primary, Java 결과는 항상 비교하고 필요 시 명시적 fallback
- YAML 구성: `engine-config.yaml`, `semantic-recipes.yaml`, `framework-catalog.yaml`
- DelegatedGuard: evaluation 기본 pack에서 제거하고 `UNRESOLVED_DELEGATED_GUARD`로 교정 평가
- 금지 경계: arbitrary expression, reflection, filesystem/network access, repository-specific field·literal·business meaning

## 선택지

### Option A: 변경 요청

`[Rationale]:`에 수정할 설계와 이유를 작성한다.

### Option B: 승인 및 Units Generation 계속 (권장)

Application Design을 승인하고 승인된 Workflow Planning에 따라 Units Generation으로 이동한다.

### Option C: 승인하되 여기서 중지

Application Design을 승인 상태로 기록하지만 Units Generation은 시작하지 않는다.

[Answer]: B
[Rationale]: 정상 scan과 YAML evaluation의 graph/bootstrap 경계 정정 내용을 포함하여 Application Design을 승인하고 Units Generation으로 진행한다.
