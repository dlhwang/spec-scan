# auto-oas × spec-scan 품질 개선 계획

## 목표

`auto-oas`의 구조화된 endpoint `FactCodeGraph`, evidence, DTO schema 강점은 유지하고,
`spec-scan`의 넓은 interprocedural 탐색과 외부 상태 조건 발견 관점을 흡수한다.
두 분석기를 병렬로 유지하는 것이 아니라 **하나의 canonical fact graph를 만들고 Rule과 LLM이
같은 사실을 소비하게 하는 것**이 최종 방향이다.

> 구현 범위는 `auto-oas`로 한정한다. `spec-scan`은 비교와 설계 판단을 위한 읽기 전용 참고 대상이며,
> 해당 저장소의 코드와 출력 계약은 변경하지 않는다.

## 현재 품질 판단

동일한 HyunSolution_BE 17개 endpoint 평가에서 두 구현 모두 endpoint와 binding은 모두 찾았다.
`auto-oas`는 DTO 구조, response status, evidence 품질이 우세했고 `spec-scan`은 repository lookup과
외부 상태 조건 recall이 우세했다. 다음 결함을 우선 해결한다.

- Java 인자명 `id`와 `@RequestHeader("Authorization")` 같은 wire name이 혼동된다.
- `findById`가 아닌 Optional repository lookup은 JDK Optional로 축소되거나 입력 origin을 잃는다.
- DB 존재 조건이 request 값 제약과 테스트 데이터 선행조건 사이에서 명확히 분리되지 않는다.
- `isMatchPassword()` 같은 위임 인증 predicate가 최종 의미로 승격되지 않는 경우가 있다.
- generic response wrapper, `@JsonIgnore`, 성공 factory의 상수값이 응답 계약에 충분히 반영되지 않는다.
- 프로젝트 전역 call/declaration graph와 endpoint별 실행 projection의 역할이 아직 분리되지 않았다.

## 설계 원칙

1. AST를 다시 걷는 별도 analyzer를 추가하지 않고 발견한 사실을 canonical graph에 기록한다.
2. 프로젝트 전역 graph는 선언·호출 해석에, endpoint projection은 제한된 의미 분석에 사용한다.
3. request 값 제약과 외부 상태 선행조건을 서로 다른 의미로 보존한다.
4. 증명하지 못한 값은 만들어내지 않고 `PARTIAL` 또는 diagnostic으로 남긴다.
5. 각 UoW는 독립 fixture RED test, 최소 구현, 관련 회귀, 전체 회귀 순으로 완료한다.

## 순차 Unit of Work

| 순서 | Unit | 목표 | 완료 조건 |
|---:|---|---|---|
| 10 | Corpus quality baseline | 실제 평가에서 드러난 결함을 작은 독립 fixture로 고정 | endpoint/binding/schema/rule/diagnostic 기대치가 테스트로 재현됨 |
| 11 | Wire-name identity | Java 인자명과 PATH/QUERY/HEADER 외부 이름을 분리 | `@RequestHeader("Authorization") String id`의 target이 `$.Authorization` |
| 12 | Repository Optional semantics | custom repository Optional lookup을 외부 존재 조건으로 분류 | qualified owner, Optional terminal, 입력 origin을 함께 증명하고 generic JDK Optional과 구분 |
| 13 | External-state prerequisite | DB/권한/인증 선행조건을 request 값 제약과 분리 | 실행 준비용 prerequisite와 순수 request condition이 혼합되지 않음 |
| 14 | Delegated authentication | boolean helper 안의 PasswordEncoder 실패 의미를 호출부까지 전파 | 인자 치환·극성·실패 branch evidence가 보존됨 |
| 15 | Response contract | wrapper generic, Jackson visibility, factory 상수를 구조화 | `@JsonIgnore` 제외, `T` 치환, success/error 상호 배타 예제가 생성됨 |
| 16 | Canonical project graph | 전역 symbol/call graph와 endpoint projection을 연결 | 별도 AST 재탐색 없이 동일 graph fact로 endpoint 결과 생성 |
| 17 | Comparative golden gate | 두 corpus의 정량 품질 저하를 CI에서 차단 | endpoint recall, resolved ratio, false-target, response coverage 기준 통과 |

## 전환 순서와 호환성

- UoW 11~15는 현재 endpoint graph에 additive fact를 먼저 추가하고 consumer를 전환한다.
- 새 consumer가 GREEN이 되기 전에는 기존 fallback을 제거하지 않는다.
- UoW 13의 출력 계약 변경은 기존 `excludedBusinessRules`를 읽는 consumer를 조사한 뒤 versioned/additive
  필드로 도입한다.
- UoW 16은 장기 구조 변경이다. 앞 단계의 정확도 개선을 막지 않으며 shadow projection 비교 후 전환한다.

각 단위의 진행 상태와 검증 명령은 [CONSTRUCTION.md](CONSTRUCTION.md)에 기록한다.
