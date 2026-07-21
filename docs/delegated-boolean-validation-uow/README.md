# Delegated Boolean Validation Unit of Work

## 목표

Controller에서 도달 가능한 도메인 생성자나 메서드가 boolean helper로 검증을 위임하는 경우, helper 호출 자체가 아니라 실제 입력 제약을 `PredicateCandidate`와 API Rule 출력까지 전달한다.

대표 대상은 다음 흐름이다.

```text
PropertyController.post
  -> PropertySaveService.save
  -> PropertySave.to
  -> ContractDetail.newInstance
  -> ContractDetail constructor
  -> if (isNotValid(contractType, deposit, rent)) throw
  -> isNotValid 내부 조건
```

기대 predicate는 다음과 같다.

```text
contractType == null
contractType == JEONSE && deposit <= 0
contractType == MONTHLYRENT && (rent <= 0 || deposit <= 0)
```

## 설계 원칙

- 모든 boolean 반환을 실패로 간주하지 않는다. 실패 의미는 호출부의 `THROW` 또는 명시적인 실패 정책에서 시작한다.
- `FailureOutcomePolicy`는 직접 `RETURN` 결과의 의미를 판정하는 확장점으로 유지한다. 메서드 간 승격 책임을 넣지 않는다.
- 호출부의 실패 극성과 helper 반환 극성을 함께 계산한다.
- 승격 후보는 내부 predicate, helper 호출, 최종 failure outcome을 evidence로 보존한다.
- 타입이나 호출 대상을 확정하지 못하면 추측해 출력하지 않고 diagnostic을 남긴다.
- 실제 외부 저장소는 테스트 실행 중 참조하지 않는다. 필요한 축약 소스는 `@TempDir` fixture로 작성한다.

## 실행 순서

1. [UOW-01 특성화와 그래프 계약](UOW-01-characterization-and-graph-contract.md)
2. [UOW-02 boolean switch 조건 합성](UOW-02-boolean-switch-path.md)
3. [UOW-03 위임 실패 조건 승격](UOW-03-delegated-failure-promotion.md)
4. [UOW-04 극성과 오탐 방지](UOW-04-polarity-and-false-positive.md)
5. [UOW-05 인자 데이터 흐름과 evidence](UOW-05-argument-flow-and-evidence.md)
6. [UOW-06 RuleOutput 회귀](UOW-06-rule-output-regression.md)
7. [UOW-07 후속 정확도 개선](UOW-07-follow-up-accuracy.md)

## 공통 완료 조건

- `FactCodeGraphBuilderTest`는 내부 검증 메서드까지의 도달성과 조건 구조를 검증한다.
- `ValidationCandidateDetectorTest`는 승격 의미와 오탐 방지를 검증한다.
- 최종 출력 테스트는 endpoint별 rule 귀속을 검증한다.
- 후보 ID와 출력 순서는 동일 입력에서 결정적이다.
- 기존 직접 `THROW`, 정책 기반 `RETURN`, validation sink 후보가 회귀하지 않는다.

