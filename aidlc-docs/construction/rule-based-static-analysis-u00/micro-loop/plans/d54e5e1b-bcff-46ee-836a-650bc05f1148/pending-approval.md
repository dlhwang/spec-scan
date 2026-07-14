# Unit 00 기준선과 분석 경계 고정 최종 개발 및 검증 계획서

## Metadata

- **Unit Slug**: `rule-based-static-analysis-u00`
- **Plan UUID**: `d54e5e1b-bcff-46ee-836a-650bc05f1148`
- **Date Finalized**: `2026-07-14T01:42:46.581Z`
- **Review Pipeline**: Stage 1 PASS | Stage 2 OKAY | Stage 3 APPROVE | Stage 4 PASS
- **Spec Reference**: `docs/rule-based-static-analysis/00-baseline-and-boundaries.md`
- **Status**: PENDING USER APPROVAL

## 아키텍처 의사결정 기록

### 결정사항

- production 분석 로직을 변경하기 전에 독립 test fixture와 migration disposition을 가진 baseline 계약을 추가한다.

### 결정 동기

1. 기존 동작과 특정 저장소 과적합 동작을 구분해야 한다.
2. 이후 Fact Graph와 Rule Engine 변경의 의도하지 않은 회귀를 측정해야 한다.
3. 실제 프로젝트만으로 검증하면 다른 이름을 가진 코드에 대한 과적합을 발견하기 어렵다.

### 기각된 대안

- **선택: 독립 baseline 테스트와 fixture 추가**
  - 장점: production 무변경, 명시적인 migration intent, 재현 가능한 경계 사례
  - 단점: 테스트 자산과 관리할 기준 데이터가 증가함
- **기각: 기존 테스트만 그대로 사용**
  - 기각 사유: 하드코딩 결과가 올바른 계약인지 교정 대상인지 구분되지 않는다.
- **기각: 바로 Fact Graph 구현 시작**
  - 기각 사유: 변경 전 기준선이 없어 회귀와 의도된 차이를 구별할 수 없다.

## Final Plan

### File Plan

1. `src/test/java/io/atworks/specscan/analysis/RuleBasedStaticAnalysisBaselineTest.java`
   - 현재 동작을 `PRESERVE`, `REPLACE`, `REMOVE`, `UNSUPPORTED`로 분류한다.
   - 대표 graph, normalization, pipeline 결과를 고정한다.
2. `src/test/resources/rule-based-static-analysis/synthetic/false-positive/UnrelatedMatchesService.java`
   - 인증과 무관한 `matches` 실패 guard를 제공한다.
3. `src/test/resources/rule-based-static-analysis/synthetic/type-resolution-failure/UnresolvedReceiverService.java`
   - 파싱 가능하지만 receiver type을 resolve할 수 없는 사례를 제공한다.
4. `src/test/resources/rule-based-static-analysis/holdout/RenamedDomainGuard.java`
   - 기존 도메인 키워드가 없는 최종 교차 검증 입력을 제공한다.
5. `src/test/resources/rule-based-static-analysis/baseline-expectations.json`
   - fixture별 현재 상태, migration disposition, 근거를 기록한다.

### 명시적 비변경 범위

- `src/main/**`
- 기존 공개 출력 스키마
- `RuleClass`, `classifyRule`, `RuleBasedConditionNormalizer`

## Verification Plan

### 단위 테스트

| 대상 | 검증 | 통과 기준 |
|:---|:---|:---|
| `RuleBasedStaticAnalysisBaselineTest` | fixture 로드, 현재 결과, disposition | 전 항목 PASS |
| `NormalizationServiceTest` | 기존 정규화 회귀 | 전 항목 PASS |
| `ValidationEvidenceGraphBuilderTest` | 기존 그래프 회귀 | 전 항목 PASS |
| `OpenApiPipelineRegressionTest` | 조립 pipeline 회귀 | 전 항목 PASS |

### 어셈블리 및 회귀 테스트

- `gradlew test` 전체 성공
- `git diff --check` 성공
- diff에 `src/main` 변경이 없음을 확인

### Evidence 기록 규칙

Goal 완료 시 다음을 기록한다.

1. 실행한 Gradle task와 exit code
2. 대상 테스트별 성공 결과
3. 전체 테스트 성공 결과
4. `git diff --check` 결과
5. 변경 파일 목록과 `src/main` 무변경 확인

## PBT Compliance

- PBT-01~08: N/A — 신규 변환 알고리즘이 없는 characterization Unit
- PBT-09: 후속 그래프/정규화 Unit에서 평가
- PBT-10: example-based baseline을 제공하므로 Compliant

## Cross-Review Trail

| 단계 | 파일 | 결과 | 잔존 리스크 |
|:---|:---|:---|:---|
| Planner | `stage-01-planner.md` | PASS | fixture 유지 비용 |
| Critic | `stage-02-critic.md` | OKAY | 환경별 타입 해석 차이 |
| Architect | `stage-03-architect.md` | APPROVE | baseline이 미래 정답으로 오용될 위험 |
| Reconciliation | `stage-04-reconciliation.md` | PASS | 없음 |

## 승인 후 Goal 범위

- G001: baseline fixture와 expectations 생성
- G002: baseline 계약 테스트 구현
- G003: 대상 및 전체 회귀 테스트 실행과 Evidence 기록

## 작성 완료 체크리스트

- [x] 모든 placeholder를 실제 값으로 대체함
- [x] 기각 대안을 기록함
- [x] 수정 대상 파일을 모두 명시함
- [x] 단위·어셈블리·회귀 검증을 포함함
- [x] Evidence 기록 규칙을 정의함
- [x] 교차 검증 4단계를 완료함
- [x] 상위 설계와 정합성을 확인함

