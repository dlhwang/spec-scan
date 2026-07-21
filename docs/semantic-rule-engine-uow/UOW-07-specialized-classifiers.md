# UOW-07 핵심 Classifier 이전

## 목표

실제 그래프 패턴이 필요한 분석기를 공유 SemanticContext 위로 이전한다.

## 대상

- enum guard constraint
- delegated boolean propagation 이후 state constraint
- PasswordEncoder failure idiom
- input-domain equality
- optimistic lock enrichment
- 제한된 YAML semantic matcher

## 작업

- 각 classifier의 입력 semantic shape와 거절 사유를 명시한다.
- 기존 snippet heuristic을 구조 fact 또는 metadata로 교체한다.
- `DelegatedGuardRule`의 enum state와 version 책임을 분리한다.
- PasswordEncoder seed 생성과 의미 분류의 중복을 제거한다.

## Interprocedural 경계

깊이는 직접 failure outcome을 가진 메서드를 Depth 0으로 계산한다.

```text
Depth 0: 직접 실패 가드가 있는 메서드
Depth 1: 가드가 호출한 boolean helper
Depth 2: helper가 다시 호출한 boolean helper
```

최대 깊이는 2다. 동일 call target 재방문은 순환으로 처리하고 해당 경로만 중단한다.

| 상황 | 처리 |
|---|---|
| target과 전체 인자 매핑 해결 | `RESOLVED` |
| target 해결, 일부 인자 origin 실패 | `PARTIAL` 후보 유지 |
| source fallback으로 target 추정 | `PARTIAL` 후보 유지 |
| target 특정 실패 | 외부 helper guard만 `PARTIAL`로 유지 |
| 실패 극성 미결정 | 내부 조건 승격 금지, 외부 guard 유지 |
| 순환 또는 Depth 초과 | 해당 경로 중단, 다른 경로 계속 |

필수 diagnostic은 `DELEGATED_MAX_DEPTH_EXCEEDED`, `DELEGATED_CALL_CYCLE`, `DELEGATED_ARGUMENT_MAPPING_PARTIAL`, `DELEGATED_TARGET_UNRESOLVED`, `DELEGATED_POLARITY_UNRESOLVED`다.

`PARTIAL` 후보는 분석 근거로 보존하지만 target path와 operator를 증명하지 못하면 `requestPreconditions` 같은 실행 가능한 조건으로 출력하지 않는다.

## 완료 조건

- 전문 classifier가 원시 graph 전체를 직접 반복 탐색하지 않는다.
- ContractDetail 복합조건이 unresolved가 아니라 numeric/enum 의미로 분류된다.
- unsupported 구조는 잘못된 rule 대신 diagnostic을 생성한다.
