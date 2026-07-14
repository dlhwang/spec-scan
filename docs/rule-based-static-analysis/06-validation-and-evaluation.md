# Work Unit 06: 검증 및 평가 체계

## 목적

분석기가 특정 프로젝트와 이름에 다시 과적합되지 않았는지 정량적·정성적으로 검증한다. 평가 결과는 Rule 추가의 품질 기준과 Unit 05 legacy 경로 제거 여부를 판단하는 근거로 사용한다.

이 Unit은 성공 사례 수만 세지 않는다. 오탐, 미탐, Evidence 손실, unresolved 남용, 미지원 구조의 잘못된 의미화를 함께 측정한다.

## 평가 단위

평가의 최소 단위는 하나의 `GoldenRuleLabel`이다. label 하나는 소스의 검증 지점 하나 또는 명시적인 음성 사례 하나를 나타낸다.

각 label은 다음 정보를 가진다.

- 안정적인 label ID
- dataset ID와 source 위치
- business rule candidate 기대 여부
- 초기 지원 범위 포함 여부
- 허용 가능한 Rule ID 집합
- 허용 가능한 category 집합
- target resolution 필수 여부

category 해석이 논쟁적이면 하나를 강제하지 않고 허용 집합을 사용한다. 의도적으로 지원하지 않는 구조는 `supportedScope=false`로 표시하며 recall 분모에서 제외하되 별도 실패 분류로 보고한다.

## 데이터셋 구성

```text
evaluation/
  positive/
    boolean-failure/
    enum-guard/
    optional-failure/
    input-domain-comparison/
  negative/
    unrelated-matches/
    lookup-without-failure/
    ordinary-control-flow/
  boundary/
    local-alias/
    nested-condition/
    type-resolution-failure/
    helper-method/
  holdout/
    renamed-domain-a/
    renamed-domain-b/
  golden-labels.json
```

fixture와 실제 프로젝트 평가는 분리한다.

- synthetic fixture: 하나의 구조와 오탐 경계를 고립해 검증한다.
- holdout fixture: Rule 구현 중 assertion 세부값에 맞춰 수정하지 않는다.
- project regression: 실제 프로젝트의 구조 변화와 통합 동작을 검증한다.

같은 Rule의 재사용으로 인정하려면 서로 다른 dataset ID에서 동일 Rule ID가 의미 해석된 결과로 관찰돼야 한다. 변수명만 바꾼 동일 파일 복제는 독립 dataset으로 세지 않는다.

## 관찰 결과 계약

평가기 입력은 label과 실제 `BusinessRuleCandidate` 목록의 쌍이다. 평가를 통과시키기 위한 별도 간소화 결과를 만들지 않는다.

- semantic match는 Rule ID와 허용 category를 모두 만족해야 한다.
- Rule ID 또는 category 허용 집합이 비어 있으면 해당 축은 강제하지 않는다.
- Evidence trace는 최소 `PREDICATE`와 `FAILURE_OUTCOME`을 모두 요구한다.
- target-required label은 `TargetResolutionStatus.RESOLVED`만 성공으로 센다.
- `PARTIAL`, `UNRESOLVED`, `UNSUPPORTED`는 서로 다른 지표로 집계한다.
- 동일 입력의 fingerprint는 후보 입력 순서와 무관해야 한다.

## 평가 지표

### Candidate precision

```text
positive label에서 관찰된 candidate 수
-------------------------------------------------
positive와 negative label에서 관찰된 candidate 수
```

known negative에서 candidate가 생성되면 false positive로 함께 기록한다.

### Semantic precision

```text
허용 Rule ID와 category에 일치한 resolved candidate 수
-----------------------------------------------------
semantic status가 RESOLVED인 candidate 수
```

### Supported-scope recall

```text
semantic match가 하나 이상 존재하는 supported positive label 수
----------------------------------------------------------
supportedScope=true인 positive label 수
```

전체 recall보다 precision과 Evidence trace를 우선하지만, unresolved를 늘려 precision만 높이는 편법을 막기 위해 supported-scope recall을 동시에 gate로 사용한다.

### 기타 지표

- false-positive count
- Evidence trace rate
- target resolution rate
- type-resolution fallback rate (`ExtractionStatus.PARTIAL`)
- unresolved rate
- unsupported rate
- unsupported 구조가 resolved 의미로 출력된 건수
- cross-dataset Rule reuse count
- legacy/new disagreement count
- 전체 관찰 candidate 수

분모가 0인 비율은 해당 평가셋에 위반 사례가 없다는 의미로 `1.0`을 사용한다. 보고서에는 원시 건수도 함께 보존해 해석 착오를 방지한다.

## 초기 품질 게이트

자동 gate의 초기 기준은 다음과 같다.

| 항목 | 기준 |
|---|---|
| Candidate precision | `1.0` |
| Semantic precision | `1.0` |
| Supported-scope recall | `0.8` 이상 |
| Evidence trace rate | `1.0` |
| Known negative false positive | `0` |
| Cross-dataset reused Rule | 최소 `1` |
| Unsupported → resolved | `0` |
| Production hardcoded domain value | `0` |
| Unintended regression | `0` |

절대 기준은 dataset이 확장되면 별도 승인으로 조정할 수 있다. 실패를 숨기기 위해 label을 `supportedScope=false`로 바꾸거나 holdout을 삭제해서는 안 된다.

legacy/new disagreement 자체는 모두 실패가 아니다. Unit 05에서 의도적으로 제거한 하드코딩과 근거 없는 status `200`은 의도된 차이로 분류한다. 승인되지 않은 차이만 unintended regression gate에 입력한다.

## 필수 테스트 계층

### Graph extraction test

소스에서 정확한 node, edge, 연산자, 극성, origin과 위치가 추출되는지 검증한다.

### Rule unit test

수작업 graph 또는 최소 source fixture에서 Rule별 양성, 유사 음성, 극성 반전과 타입 실패를 검증한다.

### Pipeline test

Java source부터 Fact Graph, Rule Engine, candidate, output adapter까지 검증한다.

### Cross-dataset regression

이름과 패키지가 다른 최소 두 dataset에서 동일 Rule ID가 재사용되는지 검증한다. 같은 실행에서 simple method name만 같은 음성 사례가 의미 Rule로 승격되지 않는지도 확인한다.

### Determinism test

동일 label과 candidate 집합의 입력 순서를 바꿔도 평가 fingerprint와 지표가 같아야 한다.

### Serialization test

평가 보고서가 JSON으로 직렬화되며 지표, 실패 분류와 fingerprint가 손실되지 않아야 한다.

## 실패 분석 형식

모든 실패는 다음 중 하나로 분류한다.

- `GRAPH_EXTRACTION_DEFECT`
- `TYPE_RESOLUTION_DEFECT`
- `CANDIDATE_DETECTION_DEFECT`
- `GRAPH_RULE_DEFECT`
- `TARGET_ORIGIN_DEFECT`
- `OUTPUT_ADAPTER_DEFECT`
- `INTENTIONALLY_UNSUPPORTED`
- `AMBIGUOUS_GROUND_TRUTH`

known negative의 의미 후보 생성은 candidate detection 또는 graph rule defect로 기록한다. supported positive가 감지되지 않으면 candidate detection defect, 감지됐지만 의미가 다르면 graph rule defect로 기록한다.

## 평가 보고서

보고서는 최소 다음을 포함한다.

- 전체 지표
- label별 실패와 실패 분류
- deterministic fingerprint
- legacy/new disagreement count
- 사용한 dataset 및 manifest version
- gate 설정과 위반 목록

보고서 생성은 동일 입력에서 결정적이어야 하며 CI에서 재현할 수 있어야 한다.

## Legacy 제거 결정

다음 조건을 모두 만족하기 전에는 legacy 경로를 제거하지 않는다.

- 초기 품질 gate 통과
- holdout과 실제 프로젝트 regression 통과
- legacy/new disagreement의 의도 여부 검토 완료
- 신규 output schema 소비자 호환성 확인
- rollback 없이 신규 경로를 기본값으로 전환할 수 있다는 승인

synthetic 평가만 통과한 상태는 legacy 제거 승인이 아니다.

## 변경 예상 파일

- evaluation label 및 report domain
- metrics evaluator와 quality gate
- deterministic report writer
- golden label manifest
- positive, negative, boundary와 holdout fixture
- cross-dataset pipeline test
- CI 또는 재현 가능한 Gradle 평가 task

## 완료 기준

- fixture, holdout과 실제 프로젝트 평가가 구분된다.
- 이름과 패키지가 다른 holdout에서 구조 기반 Rule이 동작한다.
- known negative가 의미 Rule로 잘못 승격되지 않는다.
- 정의된 지표가 자동 계산된다.
- 오탐과 미탐이 표준 실패 분류로 보고된다.
- 평가 결과가 입력 순서와 무관하게 결정적이다.
- 초기 품질 gate를 자동 테스트 또는 재현 가능한 보고서로 확인할 수 있다.
- legacy 제거 승인에 필요한 disagreement와 gate 결과가 제공된다.

## 비목표

- 평가 통과를 위한 production Rule 의미 완화
- holdout label에 맞춘 이름 기반 예외 추가
- 모든 Java 및 프레임워크 구조에 대한 전체 recall 보장
- synthetic 결과만으로 legacy 즉시 제거
