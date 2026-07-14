# Stage 03 Architect Review

## Result

APPROVE

## Architecture Decision

Rule domain과 SPI를 `analysis.domain.rule`, Fact와 candidate에 의존하는 구현을 `analysis.support.rule`에 둔다. 기존 legacy rule-based normalizer와 evidence graph에는 연결하지 않고 병렬 engine으로 구현한다.

## Dependency Direction

```text
domain.fact -----> domain.rule <----- domain.candidate
     ^                  ^                    ^
     |                  |                    |
support.fact      support.rule ------> support.candidate
```

- SPI는 immutable Fact 및 candidate domain만 입력으로 받는다.
- engine support는 Unit 02 factory와 validator를 재사용한다.
- 기존 output model은 신규 rule package에 의존하지 않는다.

## Upstream Correction Decisions

### Branch outcome

`FactMethodVisitor`는 branch body의 직접 statement를 순회하되 nested `IfStmt`, loop, lambda와 local class 내부 outcome을 상위 condition에 연결하지 않는다. nested condition은 자체 edge를 가진다.

### Evidence-aware identity

기존 `forBusinessRule(predicateId, ruleId, status)`는 호환용으로 유지한다. 신규 engine은 `forBusinessRule(predicateId, ruleId, status, evidenceFingerprint)` overload를 사용한다. fingerprint는 sorted node ID와 role로 결정한다.

## Performance Assessment

- registry snapshot: `O(R log R)` 초기 1회
- detector: 관련 node/edge `O(F)`
- Rule invocation: 최대 `P × R`
- dedup 및 conflict grouping: 평균 `O(M)`
- snapshot 정렬: `O(M log M)` 1회

## Infrastructure Assessment

외부 cache, queue, database, service와 executor는 필요하지 않다.
