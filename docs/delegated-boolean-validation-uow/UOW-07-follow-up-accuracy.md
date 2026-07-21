# UOW-07 후속 정확도 개선

## 목적

위임 boolean 승격 이후에도 남는 정확도 저하 요인을 독립적인 변경 단위로 관리한다.

## 후속 단위

1. 지역 boolean 변수의 `ASSIGNED_FROM` predicate 복원
2. Lombok·제네릭·외부 타입 해소 실패의 source fallback 강화
3. 문자열 기반 GraphRule을 구조화된 operator와 operand 기반으로 이전
4. switch expression의 block/yield 및 중첩 조건 완결성
5. 지원하지 않는 AST와 후보 누락 사유의 diagnostic 계측

## 완료 조건

- 각 항목은 독립 RED 테스트와 성능·오탐 기준을 가진 별도 변경으로 수행한다.
- 위임 boolean 구현과 한 번에 섞어 광범위하게 리팩터링하지 않는다.
