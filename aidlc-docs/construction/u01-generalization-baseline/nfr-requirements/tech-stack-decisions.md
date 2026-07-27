# U01 Generalization Baseline — Tech Stack Decisions

## 1. 결정 요약

U01은 기존 application stack 안에서 test/explicit evaluation harness로 구현한다. 신규 runtime, database, remote service, build module과 target code execution 도구를 추가하지 않는다.

| Area | Decision | Status |
| :--- | :--- | :---: |
| Language/runtime | Java 21 | Reuse |
| Build | existing Gradle module | Reuse |
| Test | JUnit 5 + existing assertion/test libraries | Reuse |
| Serialization | existing Jackson JSON support | Reuse |
| Filesystem | Java NIO `Path`/`Files` with real-path confinement | JDK |
| Digest | JDK SHA-256 | JDK |
| Artifact storage | versioned JSON + generated Markdown files | New logical format |
| Persistence | no database | Explicitly excluded |
| Network | disabled/not required | Explicitly excluded |
| Target build execution | prohibited | Explicitly excluded |
| Additional runtime | no Python/script engine | Explicitly excluded |

## 2. Java 21

### Decision

기존 Java 21 runtime과 domain model 스타일을 사용한다.

### Rationale

- existing `FactCodeGraph`, candidate, rule result와 직접 결합 가능
- record, sealed/enum과 immutable collection으로 artifact model 표현 가능
- path/digest/process-free implementation을 JDK로 제공
- 별도 runtime 설치와 cross-language schema drift 방지

### Constraints

- target repository class를 classpath에 load하지 않는다.
- reflection으로 target code나 script를 실행하지 않는다.
- process execution은 runner capability에 포함하지 않는다.

## 3. Existing Gradle Module

### Decision

U01은 현재 단일 Gradle module의 logical package와 test/evaluation resources로 구성한다. 별도 submodule을 만들지 않는다.

### Rationale

- 승인된 brownfield package/module 경계 유지
- 기존 Java engine/fixture/test runtime을 재사용
- PoC 제거와 rollback이 단순

### Placement principle

- production 정상 scan entry point는 변경하지 않는다.
- baseline domain model이 후속 Unit에서 필요하면 application package 안의 독립 baseline/evaluation 경계에 둔다.
- runner entry point와 corpus fixtures는 test 또는 명시적 evaluation source/resource 영역에 둔다.
- 구체 package/file 목록은 U01 code design/consensus planning에서 확정한다.

## 4. JUnit 5 Test Harness

### Decision

U01 acceptance evidence는 JUnit 5 기반 test/evaluation harness로 자동화한다.

### Uses

- manifest/domain rule unit tests
- path/digest/security negative tests
- required/optional corpus integration tests
- snapshot/diff approval tests
- 3-run determinism evaluation
- JSON/Markdown renderer consistency tests

### Excluded

- production CLI command 추가
- test 실행 중 remote clone/download
- baseline 자동 갱신 flag

## 5. Jackson JSON

### Decision

machine artifact는 기존 Jackson JSON stack으로 serialize/deserialize한다.

### Serialization constraints

- root에 `schemaVersion` 포함
- property와 collection ordering 결정론 적용
- unknown/unsupported fields/version 처리 정책을 명시적으로 검증
- timestamps/metrics를 semantic comparison model과 분리
- host absolute path를 serialize하지 않음

### Why not database

- artifact는 review와 version control이 핵심
- corpus 규모가 bounded batch임
- database migration/availability/backup은 U01 범위를 초과

## 6. Markdown Renderer

### Decision

Markdown summary는 JSON과 동일한 immutable evaluation result에서 생성한다.

### Boundary

- renderer는 disposition verdict, coverage 또는 gate state를 다시 계산하지 않는다.
- renderer 입력은 이미 판정된 result model이다.
- JSON과 Markdown의 overall status/count 일치 test를 둔다.

## 7. Java NIO Path Confinement

### Decision

manifest locator와 corpus root 검증은 Java NIO real-path semantics를 사용한다.

### Required behavior

1. configured allowed root를 real path로 resolve
2. corpus locator를 real path로 resolve
3. resolved corpus path가 allowed root의 descendant인지 확인
4. symlink/junction/out-of-root escape 거부
5. artifact에는 corpus-relative normalized path만 저장

Windows drive/case/junction behavior도 negative fixture에 포함한다.

## 8. SHA-256 Content Digest

### Decision

corpus와 artifact integrity identity에는 JDK SHA-256을 사용한다.

### Canonical digest input

- normalized relative path
- file content bytes
- deterministic lexicographic file ordering
- manifest에서 명시적으로 제외한 generated/build directory는 digest 대상에서 제외

mtime, owner, absolute path와 filesystem enumeration order는 digest에 포함하지 않는다.

### Purpose

- local snapshot integrity 확인
- rerun input identity
- observed snapshot/baseline version traceability

보안 서명이나 provenance attestation 용도는 아니다.

## 9. Timing and Memory Observation

### Decision

JDK가 제공하는 monotonic elapsed-time 및 process/JVM memory observation을 사용한다. 신규 profiler/agent를 필수 의존성으로 추가하지 않는다.

### Constraints

- metric은 semantic pass/fail에 포함하지 않는다.
- metric 수집 실패는 diagnostic으로 남기되 baseline semantic result를 변경하지 않는다.
- exact measurement API와 aggregation은 NFR Design에서 확정한다.

## 10. Timeout Mechanism

### Decision

timeout은 evaluation profile의 필수 양의 duration이다. hidden infinite default를 제공하지 않는다.

### Constraints

- timeout validation은 analysis 시작 전 수행
- timeout은 batch orchestration boundary에서 집행
- timeout 시 automatic retry 없음
- partial diagnostic/snapshot은 가능한 범위에서 보존
- target process를 시작하지 않으므로 external process termination은 필요 없음

구체 default duration은 없으며 각 approved evidence profile이 값을 명시한다.

## 11. Schema and Migration

### Decision

artifact별 명시적 schema version과 별도 migration path를 사용한다.

### Rules

- current loader는 선언한 supported version만 읽음
- unsupported version은 stable diagnostic으로 거부
- migration은 source/target version과 semantic diff를 출력
- normal load path에서 silent upgrade/in-place rewrite 금지
- migration tooling은 schema 변경이 실제 발생할 때 별도 scope로 설계

## 12. Excluded Technology Decisions

| Technology | Decision | Reason |
| :--- | :--- | :--- |
| relational/embedded DB | Excluded | file/version review가 핵심, bounded data |
| message queue/scheduler | Excluded | offline single-run batch |
| remote artifact store | Excluded | network independence |
| container sandbox | Not required for U01 | target code를 실행하지 않음; filesystem confinement 적용 |
| Python/data notebook | Excluded | dual runtime/schema drift 방지 |
| Gradle/Maven invocation on corpus | Prohibited | untrusted code execution 위험 |
| production CLI endpoint | Deferred | U01 runner 격리 원칙 |

## 13. Dependency Policy

- U01은 원칙적으로 신규 third-party runtime dependency를 추가하지 않는다.
- 기존 Jackson/JUnit/assertion library만 재사용한다.
- 신규 dependency가 불가피해지면 대안, license, security, deterministic behavior와 rollback 영향을 별도 승인받는다.

## 14. Verification Matrix

| Decision | Verification |
| :--- | :--- |
| Java-only/JDK | build dependency inspection |
| single module | Gradle project structure check |
| no target execution | process/build invocation negative test and source review |
| no network | offline integration test/dependency review |
| deterministic JSON | shuffled input snapshot test |
| JSON/Markdown same model | status/count consistency test |
| real-path confinement | traversal/symlink/junction test |
| SHA-256 canonicalization | ordering/mtime/absolute-path invariance tests |
| explicit timeout | missing/invalid/forced-timeout tests |
| schema fail-fast | unsupported-version tests |

## 15. Deferred Decisions

- U01 domain/model의 구체 package와 public/internal visibility
- evaluation profile의 승인된 timeout 값
- exact metric API와 memory aggregation 방식
- artifact file naming 및 output directory layout
- schema migration command/API

이 항목은 NFR Design 또는 code design에서 현재 stack 경계를 변경하지 않는 범위로 확정한다.
