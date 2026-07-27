# U01 Generalization Baseline — NFR Requirements 계획

> **상태**: NFR Requirements 완료 — 승인 대기
> **Functional Design**: `../u01-generalization-baseline/functional-design/`

## 1. 범위

U01 baseline/corpus/evidence gate가 결정적이고 안전하며 반복 가능하도록 비기능 요구사항과 기술 스택 결정을 확정한다. Production service 운영 SLO나 YAML runtime 성능은 U01 범위가 아니다.

## 2. 체크리스트

### Context and Questions

- [x] U01 Functional Design 3종 로드
- [x] Scalability, Performance, Availability, Security category 평가
- [x] Tech Stack, Reliability, Maintainability, Usability category 평가
- [x] context-specific 질문 작성
- [x] 모든 `[Answer]:` 수신
- [x] 누락·모순·모호성 검증

### Mandatory Artifacts

- [x] `../u01-generalization-baseline/nfr-requirements/nfr-requirements.md`
- [x] `../u01-generalization-baseline/nfr-requirements/tech-stack-decisions.md`
- [x] Functional/NFR requirement trace 검증
- [x] U01 NFR Requirements 승인

## 3. NFR 질문

## Question 1: Scalability profile

### Option A: 승인된 PoC 범위에 맞춘 bounded batch profile (권장)

한 evaluation run은 5~10개 corpus, corpus당 약 6~10개 endpoint 또는 동등 복잡도를 처리한다. U01은 수평 확장이나 대규모 repository service를 요구하지 않고 corpus별 독립 실행 가능성만 보장한다.

- 장점: 현재 PoC 목표와 일치하고 과도한 architecture를 피함
- 단점: 대규모 repository fleet 요구사항은 후속 단계에서 재설계 필요

### Option B: 처음부터 100개 이상 repository batch 지원

- 장점: 향후 확장 여유
- 단점: U01 범위를 넘어 scheduler/parallelism 요구가 증가

### Option C: 무제한 streaming evaluation

- 장점: 지속 평가 모델에 적합
- 단점: queue/state/운영 설계가 필요해 PoC 범위와 불일치

### Option X: 기타

[Answer]: A
[Rationale]: 5~10개 corpus의 bounded batch를 U01 capacity profile로 사용한다.

## Question 2: Performance acceptance

### Option A: 성능은 관찰하되 합격선으로 사용하지 않고 finite timeout만 적용 (권장)

corpus/endpoint별 elapsed time, peak memory, graph size를 기록한다. correctness와 determinism이 우선이며 고정 latency SLO는 두지 않는다. 무한 대기 방지를 위한 configurable evaluation timeout은 필수다.

- 장점: PoC 표현력 검증을 성능 수치가 왜곡하지 않으면서 hang은 방지
- 단점: 운영 성능 보장은 후속 단계로 남음

### Option B: repository별 고정 시간·메모리 합격선

- 장점: 명확한 성능 gate
- 단점: 실행 환경과 corpus 차이로 false failure 가능

### Option C: timeout도 두지 않고 측정만 수행

- 장점: 구현 단순
- 단점: 비정상 분석이 전체 gate를 무기한 차단 가능

### Option X: 기타

[Answer]: A
[Rationale]: correctness 우선으로 성능을 관찰하고 run profile의 유한 timeout만 필수화한다.

## Question 3: Availability and continuity

### Option A: 온라인 가용성 SLO 비적용, 재현 가능한 fail-fast batch (권장)

U01은 local/offline evaluation harness이므로 uptime, failover와 disaster recovery를 요구하지 않는다. required corpus/manifest 오류는 빠르게 실패하고 같은 pinned input으로 재실행할 수 있어야 한다.

- 장점: 실제 실행 모델에 맞음
- 단점: 중앙 evaluation service 운영 요구는 포함하지 않음

### Option B: 장시간 실행 service로 99.9% 가용성 요구

- 장점: 운영 서비스 전환에 가까움
- 단점: 배포/monitoring/infrastructure가 필요

### Option C: 실패한 corpus만 자동 retry

- 장점: 일시 오류 복구 가능
- 단점: local deterministic source에서 retry가 원인을 가릴 수 있음

### Option X: 기타

[Answer]: A
[Rationale]: 온라인 가용성 SLO 없이 pinned input으로 재현 가능한 fail-fast batch를 사용한다.

## Question 4: Untrusted repository security boundary

### Option A: static read-only, no build/code execution, no network, path confinement (권장)

corpus는 신뢰하지 않는 입력으로 취급한다. target repository의 Gradle/Maven/script를 실행하지 않고 source를 read-only로 분석한다. manifest locator는 승인된 corpus root 밖으로 탈출할 수 없으며 evaluation 중 network access를 금지한다.

- 장점: 악성 repository의 code execution과 path traversal 위험을 차단
- 단점: generated source/runtime behavior 분석이 제한됨

### Option B: dependency/type 해석을 위해 target build 실행 허용

- 장점: 더 많은 generated/type 정보를 얻을 수 있음
- 단점: PoC 보안 경계를 크게 확장

### Option C: 신뢰된 corpus만 사용하고 별도 제한 없음

- 장점: 구현 단순
- 단점: 향후 일반 repository 적용에 부적합

### Option X: 기타

[Answer]: A
[Rationale]: corpus를 untrusted input으로 취급하여 static read-only, no execution/network와 path confinement를 적용한다.

## Question 5: Tech stack

### Option A: 기존 Java 21/Gradle/JUnit 5/Jackson stack만 사용 (권장)

기존 source/test model과 JSON serialization을 재사용한다. database, remote service, 신규 scripting runtime과 별도 Gradle module을 추가하지 않는다.

- 장점: brownfield 호환성과 rollback이 가장 좋음
- 단점: 대규모 dataset 전용 도구의 편의 기능은 없음

### Option B: baseline 관리를 위한 embedded database 추가

- 장점: query/version 관리 편의
- 단점: U01 artifact가 파일 기반 review 흐름과 분리되고 의존성이 증가

### Option C: Python evaluation toolkit 병행

- 장점: 데이터 분석 편의
- 단점: 이중 runtime과 재현성 관리 필요

### Option X: 기타

[Answer]: A
[Rationale]: 기존 Java 21/Gradle/JUnit 5/Jackson stack만 사용하고 신규 runtime/service/database를 추가하지 않는다.

## Question 6: Determinism reliability evidence

### Option A: 동일 pinned input의 3회 연속 실행에서 semantic diff 0건 (권장)

serialization byte equality가 아니라 canonical semantic snapshot/diff가 3회 모두 동일해야 한다. metric/timestamp는 비교에서 제외한다.

- 장점: iteration/filesystem ordering의 우연한 일치를 탐지할 가능성이 높음
- 단점: evaluation 시간이 3배 증가

### Option B: 2회 실행 diff 0건

- 장점: 비용 절감
- 단점: 간헐적 비결정성 탐지력이 낮음

### Option C: 단위 테스트 ordering만 검증

- 장점: 빠름
- 단점: end-to-end corpus 실행의 비결정성을 놓침

### Option X: 기타

[Answer]: A
[Rationale]: 동일 pinned input을 3회 실행하여 canonical semantic diff 0건을 요구한다.

## Question 7: Schema/version maintainability

### Option A: 명시적 schema version + fail-fast migration, silent upgrade 금지 (권장)

corpus, baseline과 proposal artifact는 schema version을 가진다. 지원하지 않는 version은 거부하며 migration은 별도 명시적 도구/절차로 수행한다. loader가 구 version을 조용히 최신 의미로 해석하지 않는다.

- 장점: baseline 의미 변화의 감사 가능성 보장
- 단점: schema 변경 시 migration 관리 필요

### Option B: latest schema로 자동 변환

- 장점: 사용자 편의
- 단점: 의미 변화와 diff가 숨겨질 수 있음

### Option C: schema version 없이 현재 모델만 지원

- 장점: 초기 단순성
- 단점: baseline history 유지가 어려움

### Option X: 기타

[Answer]: A
[Rationale]: artifact schema version을 명시하고 unsupported version은 fail-fast하며 silent migration을 금지한다.

## Question 8: Evaluation usability/output

### Option A: machine-readable JSON + human-readable Markdown summary (권장)

정확한 snapshot/diff/coverage는 deterministic JSON으로 저장하고, review를 위한 요약과 gate failure 이유는 Markdown으로 생성한다. 두 출력은 동일 result model에서 파생한다.

- 장점: 자동화와 사람 검토를 모두 지원하고 결과 불일치 방지
- 단점: serializer 두 종류 유지 필요

### Option B: JSON만 제공

- 장점: 구현 단순
- 단점: disposition/re-baseline review 가독성이 낮음

### Option C: Markdown만 제공

- 장점: 사람 검토 편리
- 단점: 자동 diff/gate integration이 어려움

### Option X: 기타

[Answer]: A
[Rationale]: 동일 result model에서 deterministic JSON과 review용 Markdown summary를 생성한다.

## 4. 답변 방법

각 `[Answer]:`에 `A`, `B`, `C` 또는 `X`를 작성한다. 조합 또는 예외가 있으면 `[Rationale]:`에 적용 조건을 명시한다.

답변 검증 후 NFR Requirements와 Tech Stack Decisions를 생성한다. 명시적 승인 전 NFR Design으로 진행하지 않는다.
