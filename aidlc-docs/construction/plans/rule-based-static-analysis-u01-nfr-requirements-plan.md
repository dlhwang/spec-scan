# Unit 01 Fact Code Graph NFR Requirements 계획

## Metadata

- **Unit**: `rule-based-static-analysis-u01`
- **Stage**: NFR Requirements
- **Created**: `2026-07-14T02:13:07.928Z`
- **Status**: NFR Requirements 생성 완료, 승인 대기

## 단계 계획

- [x] Functional Design 승인 기록
- [x] cross-cutting NFR 대조
- [x] 성능·확장성·신뢰성·보안 적용성 평가
- [x] PBT 부분 적용 대상 확인
- [x] 현재 JUnit/Jqwik 호환성 조사
- [x] Tech Stack 트레이드오프 질문 작성
- [x] 사용자 답변 검증
- [x] `nfr-requirements.md` 생성
- [x] `tech-stack-decisions.md` 생성
- [ ] NFR Requirements 승인

## 적용성 평가

| NFR 영역 | 적용 여부 | 근거 |
|:---|:---|:---|
| Performance | 적용 | API별 depth, 방문 method, edge budget과 부분 결과 필요 |
| Scalability | 제한 적용 | 단일 로컬 분석 프로세스에서 repository 크기 증가를 bounded traversal로 통제 |
| Reliability | 적용 | type resolution 실패, parsing 실패, budget truncation을 격리해야 함 |
| Determinism | 적용 | 동일 소스와 설정에서 안정적 node ID 및 graph 구조 필요 |
| Security | cross-cutting 적용 | 대상 repository 코드를 실행하지 않고 source evidence를 최소 보존 |
| Availability | N/A | 로컬 CLI PoC이며 서비스 uptime 요구 없음 |
| Infrastructure | N/A | 신규 배포 또는 외부 인프라 없음 |
| Property-Based Testing | 부분 적용 | 결정적 ID와 graph invariant는 순수 함수 및 구조 변환 |

## 확정 가능한 NFR

- 기본 traversal budget은 depth 5, method 100, edge 300이다.
- budget 한도를 초과한 graph를 완전 결과처럼 표시하지 않는다.
- API별 실패를 다른 API 결과와 격리한다.
- 동일 입력과 설정은 동일 node ID를 생성한다.
- dangling edge와 duplicate node ID를 허용하지 않는다.
- 외부 source 전체를 diagnostic에 복제하지 않는다.
- PBT 실패 시 seed와 최소 shrinking case를 재현 가능하게 기록한다.

## Trade-off Question 1

Property-Based Testing 도입 시 기존 JUnit Platform과 jqwik의 버전 전략을 선택한다.

### Option A: 현재 JUnit 5.9.3 유지 + jqwik 1.7.4 추가 (Recommended)

jqwik 1.7.4가 요구하는 JUnit Platform 1.9.3 계열과 현재 프로젝트의 JUnit Jupiter 5.9.3을 맞춘다.

- **Pros**: 기존 44개 회귀 테스트의 platform 변경을 피하고 Unit 01 PBT만 추가 가능
- **Cons**: 최신 jqwik 기능을 사용하지 않으며 향후 별도 업그레이드 필요
- **Impact Analysis**:
  - Compatibility: 가장 높음, 기존 JUnit test engine 유지
  - Performance: 테스트 시 jqwik engine 실행 비용만 추가
  - Maintainability: 단기 안정성은 높지만 구버전 갱신 작업이 남음
  - Implementation Cost: 낮음
  - Risk: 낮음

### Option B: JUnit Jupiter 5.10.2 계열 + jqwik 1.8.5 동반 업그레이드

jqwik 1.8.5의 JUnit Platform 1.10.2 계열에 맞춰 Jupiter와 launcher도 같은 platform generation으로 정렬한다.

- **Pros**: 더 최신 PBT와 JUnit 기능, 중기 유지보수 기반 개선
- **Cons**: Unit 01 범위를 넘어 기존 테스트 플랫폼 전체를 변경하고 회귀 검증 부담 증가
- **Impact Analysis**:
  - Compatibility: 동반 정렬 시 호환되지만 기존 테스트 전체 재검증 필요
  - Performance: 큰 runtime 차이는 예상하지 않으나 전체 test engine 변경
  - Maintainability: 중기적으로 개선
  - Implementation Cost: 중간
  - Risk: 중간

### Option C: 최신 JUnit 5.14.4 계열 + jqwik 1.10.1 동반 업그레이드

2026-07-14 기준 jqwik 최신 release와 최소 요구 JUnit Platform을 적용한다.

- **Pros**: 최신 기능과 수정 사항 사용
- **Cons**: 이번 Fact Graph Unit에 비해 변경 범위가 크고 기존 테스트 stack과 차이가 큼
- **Impact Analysis**:
  - Compatibility: 전면 동반 업그레이드 필요
  - Performance: 검증 전 불확실
  - Maintainability: 최신 기반이나 migration 비용 큼
  - Implementation Cost: 높음
  - Risk: 높음

### Option X: Other

다른 버전 정책 또는 PBT framework를 `[Rationale]`에 기술한다.

[Answer]: A
[Rationale]: 현재 JUnit 5.9.3을 유지하고 jqwik 1.7.4를 추가한다.

## 답변 검증 결과

- Option A는 유효하며 기존 test engine과 JUnit Platform 세대가 일치한다.
- 기존 example-based 회귀 테스트의 platform migration을 Unit 01 범위에 포함하지 않는다.
- jqwik dependency 추가와 PBT 실행 검증은 승인된 Consensus Plan의 File Plan에 포함한다.


## 공식 호환 근거

- jqwik 1.7.4 user guide: JUnit Platform 1.9.3 요구
- jqwik 1.8.5 release notes: JUnit Platform 1.10.2 계열
- jqwik 1.10.1 user guide: JUnit Platform 1.14.4 요구
