# U01 Generalization Baseline — NFR Requirements 승인

> **상태**: 승인 완료 (2026-07-22)
> **산출물**: `../u01-generalization-baseline/nfr-requirements/`

## NFR 요약

- Capacity: 5~10개 corpus의 bounded offline batch
- Performance: metric 관찰, 고정 speed gate 없음, profile별 유한 timeout 필수
- Availability: online SLO 없음, preflight fail-fast와 pinned-input rerun
- Security: untrusted source, static read-only, no target build/code execution, no network, real-path confinement
- Reliability: 동일 input 3회 연속 canonical semantic diff 0건
- Maintainability: artifact별 schema version, unsupported version fail-fast, silent migration 금지
- Usability: 동일 result model에서 deterministic JSON과 Markdown 생성
- Stack: 기존 Java 21/Gradle/JUnit 5/Jackson/JDK만 사용

## 선택지

### Option A: 변경 요청

`[Rationale]:`에 수정할 NFR, acceptance evidence 또는 stack 결정을 작성한다.

### Option B: 승인 및 U01 NFR Design 계속 (권장)

NFR Requirements를 승인하고 U01 NFR Design 단계로 이동한다.

### Option C: 승인하되 여기서 중지

NFR Requirements를 승인 상태로 기록하지만 NFR Design은 시작하지 않는다.

[Answer]: B
[Rationale]: U01 NFR Requirements와 tech stack 결정을 승인하고 NFR Design으로 진행한다.
