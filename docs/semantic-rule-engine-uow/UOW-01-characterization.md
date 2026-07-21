# UOW-01 현재 동작 특성화

## 목표

구조 변경 전에 9개 기본 Java Rule과 YAML Rule의 실제 매칭, 중복, 오탐, 최종 출력을 고정한다.

## 작업

- Rule별 positive, negative, ambiguous fixture를 만든다.
- 동일 predicate에 여러 Rule이 매칭되는 경우를 기록한다.
- Detector에서 이미 의미를 판정한 관용구를 목록화한다.
- 후보당 Rule 호출 수, graph index 생성 수, edge scan 수의 baseline을 측정한다.
- Authorization의 cancellation 하드코딩 오탐을 RED 테스트로 만든다.

## 허용 변경

- 기존 rule/detector/output 테스트
- test fixture와 측정용 test support
- production 의미 변경은 금지

## 완료 조건

- 각 Rule의 현재 입출력 계약과 known defect가 테스트로 재현된다.
- ContractDetail corpus와 기존 전체 테스트가 baseline으로 기록된다.

