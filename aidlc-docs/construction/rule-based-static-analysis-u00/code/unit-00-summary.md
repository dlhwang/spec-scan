# Unit 00 구현 요약

## 결과

production 분석 로직을 변경하지 않고 정적분석 개선 전 기준선과 데이터셋 경계를 추가했다.

## 추가된 테스트 자산

- false-positive fixture: 인증과 무관한 `String.matches` guard
- type-resolution-failure fixture: classpath에 없는 receiver type
- holdout fixture: 기존 주문·권한·version 명칭을 사용하지 않는 Enum guard
- baseline manifest: 현재 결과와 `PRESERVE`, `REPLACE`, `UNSUPPORTED` migration disposition
- baseline 계약 테스트: 현재 permission 하드코딩과 generic 거부 동작을 실제 정규화 호출로 검증

## 검증 Evidence

- `RuleBasedStaticAnalysisBaselineTest`: 3/3 통과
- 관련 graph/normalization/pipeline 회귀 테스트: 성공
- 전체 테스트: 44/44 통과, 실패 0, 오류 0, 건너뜀 0
- `git diff --check`: 성공
- `src/main`: 변경 없음

## 발견한 기존 계약

`CandidateChunkGenerator`의 서비스 후보 간접 매칭은 서비스 파일명에서 `Service`를 제거한 이름이 controller class 또는 method에 포함되는지 사용한다. baseline synthetic endpoint도 이 현재 계약을 만족하도록 구성했다. 이 동작의 범용화 여부는 후속 Fact Graph 및 candidate scope Unit에서 다룬다.

## PBT Compliance

- Unit 00은 신규 변환 알고리즘을 추가하지 않아 PBT-01부터 PBT-08까지 N/A다.
- jqwik 도입 평가는 후속 그래프 변환 Unit으로 연기했다.
- example-based characterization test를 제공해 PBT-10의 상호 보완 원칙을 충족한다.

