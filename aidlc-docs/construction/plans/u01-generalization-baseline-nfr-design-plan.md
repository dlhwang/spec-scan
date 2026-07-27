# U01 Generalization Baseline — NFR Design 계획

> **상태**: NFR Design 승인 완료
> **NFR Requirements**: `../u01-generalization-baseline/nfr-requirements/`

## 1. 설계 범위

U01 NFR을 만족할 resilience, bounded execution, timeout, security confinement, determinism, immutable artifact publication, schema migration과 metrics logical components를 설계한다. Production infrastructure, distributed scheduler와 remote storage는 범위 밖이다.

## 2. 체크리스트

### Context and Questions

- [x] U01 NFR Requirements와 Tech Stack Decisions 로드
- [x] Resilience, Scalability, Performance, Security, Logical Components category 평가
- [x] context-specific pattern 질문 작성
- [x] 모든 `[Answer]:` 수신
- [x] 누락·모순·모호성 검증

### Mandatory Artifacts

- [x] `../u01-generalization-baseline/nfr-design/nfr-design-patterns.md`
- [x] `../u01-generalization-baseline/nfr-design/logical-components.md`
- [x] NFR requirement/pattern/component trace 검증
- [x] U01 NFR Design 승인

## 3. NFR Design 질문

## Question 1: Resilience와 failure recovery pattern

### Option A: Fail-fast preflight + diagnostic-preserving abort, 자동 retry 없음 (권장)

manifest/schema/path/digest/timeout 검증을 analysis 전에 수행한다. required failure 또는 runtime timeout은 run을 중단하되 이미 수집한 diagnostic과 partial evidence를 보존한다. 재실행은 입력 수정 후 명시적으로 수행한다.

- 장점: 오류 원인을 숨기지 않고 재현 가능
- 단점: 일시 오류도 자동 복구하지 않음

### Option B: corpus별 최대 3회 자동 retry

- 장점: 일시적 filesystem 오류 복구 가능
- 단점: deterministic local input에서 defect를 가릴 수 있음

### Option C: 가능한 corpus는 모두 처리한 뒤 마지막에 실패

- 장점: 더 많은 diagnostic 수집
- 단점: required precondition 실패 후 불필요한 분석 수행

### Option X: 기타

[Answer]: A
[Rationale]: preflight fail-fast와 diagnostic-preserving abort를 사용하고 자동 retry를 금지한다.

## Question 2: Bounded execution/scalability pattern

### Option A: 결정적 sequential reference executor (권장)

corpus와 endpoint를 canonical order로 순차 실행한다. U01 G01 evidence는 이 reference mode만 사용하며 parallel execution은 후속 최적화로 남긴다.

- 장점: 상태 격리와 결정론 검증이 가장 단순
- 단점: 전체 evaluation 시간이 길어질 수 있음

### Option B: corpus-level bounded parallel executor

- 장점: corpus 간 독립성을 이용해 시간 단축
- 단점: resource contention과 ordering canonicalization 부담

### Option C: endpoint-level parallel executor

- 장점: 최대 병렬성
- 단점: shared parser/type resolver와 메모리 경쟁 위험이 큼

### Option X: 기타

[Answer]: A
[Rationale]: G01 evidence는 canonical order의 sequential reference executor로 생성한다.

## Question 3: Timeout 집행 pattern

### Option A: Run deadline + cooperative corpus/endpoint checkpoints (권장)

evaluation profile의 필수 timeout을 monotonic deadline으로 변환한다. corpus/endpoint 시작 전과 주요 단계 경계에서 remaining time을 검사하고 만료 시 `EVALUATION_TIMEOUT`과 partial evidence를 반환한다. thread 강제 종료는 사용하지 않는다.

- 장점: 안전한 종료와 partial diagnostic 보존
- 단점: 긴 단일 parser 호출 중에는 즉시 중단되지 않을 수 있음

### Option B: executor future timeout과 thread interrupt

- 장점: 더 빠른 취소 가능
- 단점: parser/state가 interrupt-safe하지 않으면 불완전 상태 위험

### Option C: corpus별 독립 timeout만 적용

- 장점: corpus별 제어가 명확
- 단점: 전체 run deadline 보장이 약함

### Option X: 기타

[Answer]: A
[Rationale]: monotonic run deadline과 cooperative corpus/endpoint checkpoint로 timeout을 집행한다.

## Question 4: Security confinement pattern

### Option A: Preflight `CorpusAccessGuard` + confined read-only view (권장)

모든 locator를 real path로 resolve하고 approved root descendant 여부와 symlink/junction escape를 검사한다. 검증된 relative path view만 analyzer에 전달하며 process/network/write capability는 component graph에 제공하지 않는다.

- 장점: deny-by-construction과 중앙 검증 경계 제공
- 단점: Windows junction/case 및 symlink fixture가 필요

### Option B: 각 reader가 자체 path 검증

- 장점: 별도 guard component 불필요
- 단점: 검증 중복과 누락 위험

### Option C: OS sandbox/container에만 의존

- 장점: 강한 외부 격리 가능
- 단점: U01 환경 의존성이 커지고 application-level 검증이 사라짐

### Option X: 기타

[Answer]: A
[Rationale]: 중앙 CorpusAccessGuard와 confined read-only view로 path와 capability를 제한한다.

## Question 5: Determinism pattern

### Option A: Canonicalization pipeline + three-run verifier (권장)

raw observation을 canonicalizer가 정규화·정렬한 뒤 immutable semantic snapshot을 만든다. `DeterminismVerifier`가 같은 input을 3회 실행하고 canonical snapshot을 비교한다. renderer는 canonical result만 소비한다.

- 장점: 비결정성 원인과 rendering을 분리
- 단점: 세 번의 full run 비용

### Option B: serializer에서만 key/order 정렬

- 장점: 구현 단순
- 단점: runtime selection 비결정성을 숨길 수 있음

### Option C: golden file byte equality만 검증

- 장점: 강한 출력 일치
- 단점: timestamp/metric 차이와 semantic diff를 분리하기 어려움

### Option X: 기타

[Answer]: A
[Rationale]: canonicalization pipeline 뒤에서 같은 pinned input을 비교하는 three-run verifier를 사용한다.

## Question 6: Immutable artifact publication pattern

### Option A: Versioned bundle + completion manifest last-write (권장)

새 unique version directory에 JSON/Markdown/digest를 작성하고 내부 consistency를 검증한 뒤 completion manifest를 마지막에 기록한다. consumer는 completion manifest가 유효한 bundle만 읽으며 기존 approved bundle을 덮어쓰지 않는다.

- 장점: 플랫폼별 atomic directory move 의존 없이 partial artifact 노출 방지
- 단점: incomplete staging bundle 정리 정책이 필요

### Option B: 임시 directory를 atomic move

- 장점: 성공 시 단순한 원자적 publish
- 단점: filesystem/Windows 환경에서 atomic move 보장 차이

### Option C: 기존 파일을 직접 overwrite

- 장점: 구현 단순
- 단점: 중간 실패 시 baseline 손상 가능

### Option X: 기타

[Answer]: A
[Rationale]: 새 version bundle을 작성하고 consistency 검증 후 completion manifest를 마지막에 기록한다.

## Question 7: Schema migration pattern

### Option A: Strict version reader registry + 별도 explicit migrator (권장)

normal loader는 정확히 지원하는 version reader만 선택한다. migration은 source/target version을 명시한 별도 component가 새 artifact를 생성하고 semantic diff를 출력한다. 원본은 보존한다.

- 장점: silent meaning change 방지와 감사 가능성
- 단점: version별 reader/migrator 유지 필요

### Option B: loader chain이 자동으로 latest로 변환

- 장점: 사용 편의
- 단점: read와 migration이 결합되어 변화가 숨겨짐

### Option C: 항상 current version만 지원하고 과거 artifact 폐기

- 장점: 유지보수 최소
- 단점: baseline history와 재현성 상실

### Option X: 기타

[Answer]: A
[Rationale]: strict version reader registry와 원본 보존형 explicit migrator를 분리한다.

## Question 8: Metrics logical component

### Option A: Best-effort `RunMetricsCollector`, semantic result와 분리 (권장)

monotonic elapsed time과 JDK memory observation을 corpus/endpoint 단계에서 수집한다. metric 수집 실패는 diagnostic으로 기록하지만 semantic verdict를 바꾸지 않는다. metric은 canonical diff에서 제외한다.

- 장점: 관찰성과 의미 정확성 분리
- 단점: memory 값은 환경별 근사치

### Option B: metric 수집 실패도 run 실패

- 장점: 완전한 metric 보장
- 단점: 비본질적 관찰 실패가 G01을 차단

### Option C: 외부 profiler/agent 필수

- 장점: 정밀 측정
- 단점: 신규 runtime dependency와 실행 복잡도 증가

### Option X: 기타

[Answer]: A
[Rationale]: best-effort metrics collector를 semantic result와 분리하고 수집 실패를 diagnostic으로만 처리한다.

## 4. 답변 방법

각 `[Answer]:`에 `A`, `B`, `C` 또는 `X`를 작성한다. 조합 선택 시 component별 적용 기준을 `[Rationale]:`에 명시한다.

답변 검증 후 `nfr-design-patterns.md`와 `logical-components.md`를 생성한다. 명시적 승인 전 implementation planning으로 진행하지 않는다.
