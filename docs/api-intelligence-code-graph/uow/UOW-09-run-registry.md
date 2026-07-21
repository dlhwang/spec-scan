# UOW-09 Thread-safe Run Registry와 상태 전이

## Goal

단일 active run과 immutable status snapshot을 관리하는 process-local registry를 구현한다.

## 선행 조건

UOW-01 완료.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/port/out/RunProgressPort.java`
- `src/main/java/io/atworks/apiintelligence/application/InMemoryRunRegistry.java`
- `src/main/java/io/atworks/apiintelligence/application/RunStateMachine.java`
- `src/main/java/io/atworks/apiintelligence/application/AnalysisRunSnapshot.java`
- 해당 concurrency 테스트

## 필수 계약

- 상태: QUEUED, ACQUIRING, DISCOVERING, GRAPHING, ANALYZING, SAVING, COMPLETED, PARTIAL, FAILED
- 허용 transition 표 밖의 이동을 거부한다.
- worker만 atomic transition/counter update를 수행한다.
- HTTP에는 immutable snapshot만 반환한다.
- terminal result 위치는 artifact finalization 후 한 번만 publish한다.
- active run은 1개, 대기 run은 0개다.
- 두 번째 run은 `RUN_CAPACITY_EXCEEDED`
- terminal snapshot은 최대 20개 또는 24시간 중 먼저 충족 시 제거
- registry 제거는 artifact를 삭제하지 않는다.
- shutdown 진입 후 신규 run을 거부한다.

## 구현 절차

1. transition table과 immutable snapshot을 정의한다.
2. active run admission을 compare-and-set 방식으로 구현한다.
3. atomic counters와 terminal publication을 구현한다.
4. retention eviction과 shutdown gate를 구현한다.

## 테스트와 완료 조건

- illegal transition과 double terminal publish
- concurrent admission에서 정확히 하나만 성공
- concurrent increment/snapshot의 count invariant
- retention/eviction이 artifact에 영향 없음
- 반복 concurrency test 안정 통과

