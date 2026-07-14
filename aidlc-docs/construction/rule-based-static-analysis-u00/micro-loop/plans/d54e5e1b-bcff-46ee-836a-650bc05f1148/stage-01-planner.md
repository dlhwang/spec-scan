# Stage 1: Planner — Unit 00 기준선과 분석 경계 고정

## 실행 체크리스트

- [x] 기존 설계 문서와 AI-DLC 상태 확인
- [x] Unit 00 범위와 비목표 확인
- [x] 관련 테스트와 빌드 설정 확인
- [x] Intent Diff 작성
- [x] 수정 파일 계획 작성
- [x] 검증 계획 작성

## Intent Diff

| 영역 | AS-IS | TO-BE |
|:---|:---|:---|
| 기존 동작 | 여러 테스트에 흩어진 assertion으로만 존재 | 유지, 교정, 제거 대상을 명시한 baseline 계약으로 고정 |
| 과적합 결과 | `currentUser`, 취소 권한 연산자, 주문 상태 상수가 정상 결과처럼 검증됨 | 현재 출력은 기록하되 미래 의도를 `REPLACE`로 분류 |
| 음성 사례 | 메서드명과 문자열 중심의 일부 사례만 존재 | 구조가 유사하지만 의미가 다른 false-positive fixture 추가 |
| 타입 해석 실패 | 개별 테스트 상황에 묻혀 있음 | 별도 boundary fixture로 재현 가능하게 고정 |
| holdout | 구현 테스트와 분리된 데이터셋 없음 | Rule 구현 시 직접 맞추지 않는 물리적 holdout 디렉터리 마련 |
| production 동작 | Unit 00에서 변경될 위험 | 변경하지 않음 |

## 구현 원칙

1. Unit 00에서는 `src/main`을 수정하지 않는다.
2. 현재 동작을 올바른 설계로 승인하지 않고 `PRESERVE`, `REPLACE`, `REMOVE`, `UNSUPPORTED` 의도로 분류한다.
3. fixture는 특정 프로젝트 소스 복사본이 아니라 최소 독립 Java 예제로 만든다.
4. holdout의 세부 기대값은 초기 Rule 구현 테스트에서 참조하지 않는다.
5. 타입 해석 실패는 분석기 예외가 아니라 재현 가능한 baseline 결과로 고정한다.

## File Plan

### 1. `src/test/java/io/atworks/specscan/analysis/RuleBasedStaticAnalysisBaselineTest.java`

- **Change Type**: New
- `NormalizationService`, `ValidationEvidenceGraphBuilder`, pipeline의 현재 동작을 대표 assertion으로 묶는다.
- 각 assertion에 migration intent를 드러내는 테스트명 또는 `BaselineDisposition`을 사용한다.
- 최소 검증:
  - repository/graph reachability처럼 유지할 동작
  - `$.currentUser`, `HAS_CANCELLATION_PERMISSION`, `PAYMENT_WAITING,PREPARING`처럼 교정할 동작
  - 미지원 generic hint 거부 동작
  - false-positive fixture가 비즈니스 의미로 승격되는 현재 여부
  - 타입 미해석 fixture의 실패 또는 degraded 동작

### 2. `src/test/resources/rule-based-static-analysis/synthetic/false-positive/UnrelatedMatchesService.java`

- **Change Type**: New
- `String.matches` 또는 의미가 다른 `matches` 호출과 실패 분기를 포함한다.
- 향후 PasswordEncoder Rule이 method name만으로 인증 의미를 부여하지 않는 음성 기준으로 사용한다.

### 3. `src/test/resources/rule-based-static-analysis/synthetic/type-resolution-failure/UnresolvedReceiverService.java`

- **Change Type**: New
- classpath에 없는 receiver type을 사용해 타입 해석 실패를 재현한다.
- 파싱은 가능하지만 resolved signature를 얻지 못하는 경계를 고정한다.

### 4. `src/test/resources/rule-based-static-analysis/holdout/RenamedDomainGuard.java`

- **Change Type**: New
- 주문, 권한, 상태, version 같은 기존 키워드를 사용하지 않는 검증 guard를 둔다.
- Unit 04 Rule 구현 중 직접적인 기대값 튜닝 대상으로 사용하지 않는다.

### 5. `src/test/resources/rule-based-static-analysis/baseline-expectations.json`

- **Change Type**: New
- fixture ID, 현재 상태, migration disposition, 근거를 기계 판독 가능한 형태로 기록한다.
- 실행 결과 전체 snapshot이 아니라 안정적인 계약 필드만 저장한다.

## 검증 계획

- 대상 테스트 클래스 단독 실행
- 기존 `NormalizationServiceTest`, `ValidationEvidenceGraphBuilderTest`, `OpenApiPipelineRegressionTest` 실행
- 전체 `gradlew test` 실행
- `git diff --check` 실행
- `src/main` 변경이 없음을 diff로 확인

## PBT Compliance

- PBT-01: N/A — Unit 00은 신규 순수 변환 로직을 구현하지 않고 고정 fixture의 현재 동작을 기록한다.
- PBT-02~08: N/A — 생성 범위나 상태 공간을 가진 신규 알고리즘이 없다.
- PBT-09: Deferred — jqwik 도입 여부는 그래프 정규화 Unit의 NFR/계획에서 다룬다.
- PBT-10: Compliant — 현재 Unit은 example-based regression baseline을 우선 제공한다.

