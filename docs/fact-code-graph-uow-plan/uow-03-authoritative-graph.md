# UoW 03 — FactCodeGraph 단일 기준

## 목적

신규 분석 기능의 authoritative source를 `FactCodeGraph`로 통일한다.

## 작업 범위

- GraphRule 입력을 `FactCodeGraph`로 통일
- candidate부터 exporter까지 Fact node ID 보존
- normalizer와 exporter의 source snippet 재해석 차단
- `ValidationEvidenceGraph`를 legacy 호환 또는 projection 용도로 제한

## 목표 흐름

```text
Source AST
→ FactCodeGraph
→ GraphRule
→ BusinessRuleCandidate
→ Output Adapter
→ Execution Spec
```

## 비범위

- 이 UoW에서 legacy graph를 즉시 제거하지 않는다.
- 생성자나 lambda coverage를 두 그래프에 중복 구현하지 않는다.

## 완료 조건

- 신규 규칙이 `ValidationEvidenceGraph` 없이 동작한다.
- output evidence까지 Fact node ID가 유지된다.
- snippet-only candidate가 출력되지 않는다.

