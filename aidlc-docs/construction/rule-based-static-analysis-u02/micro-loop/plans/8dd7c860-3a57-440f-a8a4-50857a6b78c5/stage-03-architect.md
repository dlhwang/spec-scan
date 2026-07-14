# Stage 03 Architect Review

## Result

APPROVE

## Architecture Decision

후보 모델을 기존 analysis domain에 덧붙이지 않고 `analysis.domain.candidate` package로 분리한다. Fact 의존 변환은 `analysis.support.candidate`에 한정하며 candidate domain 자체는 JavaParser, Spring, legacy graph와 기존 output model에 의존하지 않는다.

## Dependency Direction

```text
domain.candidate <- support.candidate -> domain.fact
       ^                  ^
       |                  |
     tests             tests
```

- candidate domain은 fact domain에 직접 의존하지 않고 node ID와 source 좌표 값만 보존한다.
- support mapper와 classifier만 fact domain을 읽는다.
- 기존 output model은 신규 candidate package에 의존하지 않는다.

## Invariant Boundaries

- record constructor: 필드 null, blank, 범위, collection 복사
- `CandidateInvariantValidator`: status/category/ruleId/target/diagnostic 조합
- `CandidateResultIntegrityValidator`: predicate 및 evidence 참조, ID uniqueness
- accumulator: duplicate 조기 검출과 조건 단위 failure isolation

## Performance Assessment

Fact node 및 candidate ID set을 한 번 구성해 생성과 검증을 `O(F + C)`로 유지한다. 결과 정렬은 snapshot 시 ID 기준 `O(C log C)` 한 번만 수행한다.

## Infrastructure Assessment

신규 cache, database, queue, remote service, 배포 변경은 필요하지 않다.
