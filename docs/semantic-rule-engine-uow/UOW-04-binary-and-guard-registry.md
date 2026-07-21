# UOW-04 Binary classifier와 Guard Registry

## 목표

직접 조건식과 표준 guard method를 서로 다른 메커니즘으로 분류한다.

## 작업

- `BinaryConstraintClassifier`에서 null, literal, enum, numeric operator를 처리한다.
- failure polarity를 적용해 요청 조건으로 반전한다.
- `StandardGuardMethodRegistry`에 qualified signature와 argument index를 선언한다.
- null/empty Rule의 표준 호출 분기를 registry로 이동한다.
- direct `value == null` 분석은 binary classifier에 유지한다.

## 완료 조건

- null 직접 비교와 `requireNonNull`이 동일한 `NormalizedConstraint`를 만든다.
- `deposit <= 0`에서 유효 입력 조건의 정확한 경계와 inclusive 여부를 보존한다.
- resolved signature가 다른 동명 메서드를 매칭하지 않는다.

