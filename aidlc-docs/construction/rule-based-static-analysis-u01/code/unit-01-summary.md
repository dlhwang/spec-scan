# Unit 01 Fact Code Graph 구현 요약

## 결과

기존 `ValidationEvidenceGraph`와 별개로 관찰 사실만 저장하는 immutable Fact Code Graph를 구현했다.

## 구현 내용

- 공통 `FactNode`와 sealed payload records
- typed `FactEdge`와 operand/argument/branch 관계
- start/end line과 column을 포함한 `SourceRange`
- resolved, partial, unresolved, not-applicable 타입 상태
- source 좌표와 AST role 기반 SHA-256 결정적 ID
- immutable graph와 dangling edge/duplicate ID 검증
- 기본값 depth 5, methods 100, edges 300 복합 budget
- API별 traversal context와 truncation diagnostic
- application source만 방문하는 traversal policy
- 외부 호출의 call fact와 signature 보존
- 직접 재귀 method node 재사용 및 본문 재방문 차단
- 지역 변수 initializer origin과 Enum constant 추출
- jqwik 1.7.4 property tests

## 검증 Evidence

- 신규 fact tests: 9/9 통과
- example-based tests: 6개
- jqwik properties: 3개
- PBT tries: 500, 500, 200
- PBT seed: JUnit XML에 각 property별 기록
- 전체 suite: 53/53 통과
- failures/errors/skips: 0/0/0
- JUnit Platform: 1.9.3으로 Jupiter와 jqwik 일치
- legacy graph 관련 파일 변경: 없음
- 작업 범위 scoped `git diff --check`: 통과

## 검증 중 발견하고 수정한 결함

1. jqwik `IntRange` import 누락을 blocker Goal로 기록하고 수정했다.
2. method call operand가 동일 call node를 다시 방문해 self-edge를 만들 수 있는 경로를 제거했다.
3. API root와 resolved target의 method identity 형식 차이로 재귀 본문이 중복 node가 되는 문제를 canonical signature와 node registry로 수정했다.

## 기존 작업트리 주의사항

전역 `git diff --check`는 이번 Unit과 무관한 기존 `poc_report.md` 16~17행 trailing whitespace 때문에 실패한다. 해당 사용자 파일은 수정하지 않았다. 이번 Unit과 AIDLC 문서 범위의 scoped diff check는 통과했다.

## PBT Compliance

- PBT-01~04: identity 및 graph invariant로 충족
- PBT-07: constrained SourceRange와 role generator 사용
- PBT-08: shrinking 유지 및 seed 기록
- PBT-09: jqwik 1.7.4와 JUnit Platform 1.9.3 정렬
- PBT-10: example-based 6개와 property 3개 병행

