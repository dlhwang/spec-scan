# Stage 4: Intent Reconciliation — Unit 01 Fact Code Graph

## Deep Interview Gating

- **Result**: SKIPPED
- Functional Design과 NFR 승인 과정에서 payload, ID, traversal budget, PBT 버전을 확정했다.
- T1~T5에 해당하는 미해결 구현 선택이 없다.

## Intent Mapping Matrix

| ID | 결정 또는 제약 | 구현 대상 | 상태 |
|:---|:---|:---|:---|
| D1 | 공통 FactNode와 sealed payload | domain fact files | MATCH |
| D2 | source coordinate와 AST role ID | ID generator + PBT | MATCH |
| D3 | depth 5/method 100/edge 300 | budget/context/builder tests | MATCH |
| D4 | 프로젝트 내부 관련 메서드만 방문 | traversal policy | MATCH |
| D5 | 제외 호출도 사실 기록 | call visitor test | MATCH |
| D6 | cycle 차단 | traversal context + cycle test | MATCH |
| D7 | truncation diagnostic | diagnostic collector + budget test | MATCH |
| C1 | legacy graph 유지 | 신규 package, 기존 파일 비수정 | MATCH |
| C2 | 비즈니스 의미 저장 금지 | domain enum 및 payload review | MATCH |
| C3 | snippet 재파싱 금지 | AST visitor 직접 변환 | MATCH |
| C4 | JUnit 유지 + jqwik 1.7.4 | build.gradle + full regression | MATCH |
| C5 | static-analysis-only | builder test 및 dependency review | MATCH |

## Reconciliation Result

- **Coverage**: 100% (12/12)
- **Verdict**: PASS
- **Discrepancies**: 없음

