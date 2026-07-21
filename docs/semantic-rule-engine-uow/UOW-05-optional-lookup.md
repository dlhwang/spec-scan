# UOW-05 Optional Lookup 통합

## 목표

일반 Optional과 Spring Data lookup을 하나의 specificity-aware classifier에서 처리한다.

## 작업

- terminal `orElseThrow`와 receiver chain을 한 번 분석한다.
- Spring Data resolved signature면 구체 lookup 결과를 생성한다.
- 그 외 JDK Optional이면 일반 fallback 결과를 생성한다.
- 한 predicate에서 두 existence candidate가 생성되지 않게 한다.
- 기존 precedence가 보존하던 의미 차이를 명시적 subtype으로 표현한다.

## 완료 조건

- 기존 두 Rule의 positive corpus가 통과한다.
- Spring Data 결과가 일반 Optional 결과에 의해 덮어써지지 않는다.
- shadow 비교 후 두 기존 Rule을 제거할 수 있다.

