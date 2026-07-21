# UOW-01 특성화와 그래프 계약

## 목적

현재 그래프가 API에서 `isNotValid` 내부까지 도달하지만 검출 결과가 `isNotValid(...)` 하나에 머무는 경계를 테스트로 고정한다.

## 허용 변경

- `ContractDetailValidationFixture`
- `FactCodeGraphBuilderTest`
- `ValidationCandidateDetectorTest`

## 작업

- 외부 RealEstate 경로를 참조하지 않는 축약 소스를 `@TempDir`에 작성한다.
- API→서비스→DTO 변환→정적 factory→생성자→helper 호출 경로를 검증한다.
- null, JEONSE, MONTHLYRENT 조건 fact 존재를 검증한다.
- 내부 조건 승격 기대 테스트를 RED로 추가한다.

## 완료 조건

- 그래프 테스트는 GREEN이다.
- 후보 승격 테스트는 기존 구현에서 `predicates=[isNotValid(...)]`로 RED이다.

