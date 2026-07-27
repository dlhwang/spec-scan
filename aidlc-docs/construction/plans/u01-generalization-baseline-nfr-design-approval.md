# U01 Generalization Baseline — NFR Design 승인

> **상태**: 승인 완료 (2026-07-22T12:28:51.1231714+09:00)
> **산출물**: `../u01-generalization-baseline/nfr-design/`

## 설계 요약

- Preflight gate에서 schema/path/digest/timeout을 analysis 전에 검증한다.
- required failure는 diagnostic-preserving abort, 자동 retry는 금지한다.
- G01 evidence는 sequential reference executor로 생성한다.
- monotonic run deadline과 cooperative checkpoint를 사용한다.
- `CorpusAccessGuard`가 confined read-only view만 analyzer에 제공한다.
- canonicalization 후 외부 `DeterminismVerifier`가 `SingleRunCoordinator`를 3회 호출한다.
- artifact는 versioned bundle에 작성하고 completion manifest를 마지막에 기록한다.
- exact schema reader registry와 explicit migrator를 분리한다.
- best-effort metrics는 semantic verdict와 분리한다.
- 정상 production scan은 U01 evaluation component에 의존하지 않는다.

## 선택지

### Option A: 변경 요청

`[Rationale]:`에 수정할 pattern, logical component 또는 dependency를 작성한다.

### Option B: 승인 및 U01 implementation planning 계속 (권장)

NFR Design을 승인하고 U01 Consensus Planning/implementation evidence 단계로 이동한다.

### Option C: 승인하되 여기서 중지

NFR Design을 승인 상태로 기록하지만 implementation planning은 시작하지 않는다.

[Answer]: B
[Rationale]: 사용자 응답 `승인`에 따라 U01 NFR Design을 승인하고 U01 implementation planning으로 계속한다.
