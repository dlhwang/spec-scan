# Work Unit 08: 사용자 정의 YAML Rule Pack PoC

## 목적

기본 Rule Pack이 알지 못하는 프로젝트 전용 Validator 또는 helper 호출 구조를 사용자가 YAML로 선언하고, 기존 `GraphRule` 실행 계약과 `FactCodeGraph`에 그대로 적용할 수 있는지 검증한다.

이 Unit은 Rule 관리 제품을 만드는 작업이 아니다. 안전하게 제한된 YAML 입력을 기존 Rule Registry에 추가하여 다음 흐름이 성립하는지만 확인한다.

```text
YAML 파일 또는 문자열
  -> 문법 및 필드 검증
  -> YamlGraphRule 생성
  -> 기본 Rule Pack과 합성
  -> 기존 RulePackRegistry / DefaultGraphRuleEngine 실행
  -> BusinessRuleCandidate
  -> CandidateToOutputAdapter
```

## 구현 범위

- 하나의 YAML에 복수 Rule 정의
- UTF-8 로컬 파일 또는 문자열 로딩
- YAML 문법 오류와 필수 필드 누락 진단
- YAML 내부 및 기본 Rule Pack과의 Rule ID 충돌 진단
- 유효하지 않은 Rule만 제외하고 나머지 Rule 유지
- 기본 Rule과 사용자 Rule 동시 등록 및 실행
- Rule별 평가, 매칭, 실패 횟수 보고
- Rule ID, 호출 위치, 조건식과 실패 분기를 포함한 Evidence 생성
- `BusinessRuleCandidate`와 `excludedBusinessRules` 출력 변환

## YAML 계약

```yaml
rules:
  - id: PROJECT_POLICY_VALIDATION
    match:
      predicateType: BOOLEAN_CALL
      methodName: validateProjectPolicy
      resolvedSignatureContains: ProjectPolicy.validateProjectPolicy
      failureOutcome: THEN
    output:
      category: INVARIANT
      constraint:
        kind: CONTROL_FLOW_ONLY
```

### `match`

| 필드 | 의미 | PoC 규칙 |
|---|---|---|
| `predicateType` | 후보 탐지기가 만든 조건 종류 | `PredicateType` 값과 정확히 일치해야 한다. |
| `methodName` | 조건식 하위 호출의 메서드명 | `resolvedSignatureContains`와 둘 중 하나 이상 필요하다. |
| `resolvedSignatureContains` | 타입 해석된 호출 시그니처의 일부 | 타입 해석에 실패하면 이 조건은 매칭되지 않는다. |
| `failureOutcome` | 실패가 연결된 분기 | `THEN`, `ELSE`, `ANY`만 허용한다. |

`methodName`과 `resolvedSignatureContains`를 모두 쓰면 두 조건을 모두 만족해야 한다. 호출은 조건 노드에서 `OPERAND_OF` 또는 `ASSIGNED_FROM`으로 관찰 가능한 하위 그래프만 탐색한다.

### `output`

`category`는 기존 `BusinessRuleCategory` 값 중 하나다. PoC의 constraint는 `CONTROL_FLOW_ONLY`만 허용한다.

이 제한은 의도적이다. 사용자 YAML에 `targetPath`, `operator`, `expectedValue`를 적었다는 이유만으로 해당 값이 소스에서 관찰된 사실이 되지는 않는다. 따라서 현재 구현은 이 필드를 허용하지 않고, 호출 구조와 실패 흐름만 확인된 후보를 `excludedBusinessRules`로 출력한다.

## 실행 계약

```java
ConfiguredRulePacks configured = new YamlRulePackComposer()
    .compose(InitialRulePacks.all(), Path.of("project-rules.yaml"));

DefaultGraphRuleEngine engine = new DefaultGraphRuleEngine(
    candidateDetector,
    configured.packs()
);
```

- `configured.loadedUserRuleCount()`로 등록된 사용자 Rule 수를 확인한다.
- `configured.diagnostics()`로 로딩 및 등록 실패 사유를 확인한다.
- `GraphRuleEngineResult.report().ruleMetrics()`로 Rule별 평가, 매칭, 실패 횟수를 확인한다.
- 매칭 위치와 생성 결과는 `BusinessRuleCandidate.evidence()` 및 출력 모델의 Evidence에서 확인한다.

## 오류 격리

| 오류 | 처리 |
|---|---|
| YAML 문법 오류 | 사용자 Rule을 등록하지 않고 `YAML_SYNTAX_ERROR`를 반환한다. |
| Rule 필수 필드 누락 | 해당 Rule만 제외한다. |
| 알 수 없는 필드 | 해당 Rule을 제외하여 묵시적 오타를 허용하지 않는다. |
| YAML 내부 중복 ID | 뒤의 중복 Rule을 제외한다. |
| 기본 Rule과 ID 충돌 | 사용자 Rule을 제외하고 기본 Rule을 유지한다. |
| 실행 중 사용자 Rule 예외 | 기존 `RuleInvocationBoundary`가 격리하며 다른 Rule 실행을 계속한다. |

YAML은 데이터로만 파싱하며 Java 코드, 클래스, 스크립트 또는 임의 플러그인을 로딩하지 않는다.

## 검증 시나리오

1. 기본 Rule Pack과 사용자 Rule Pack이 같은 Registry에 등록된다.
2. 기본 Rule이 모르는 `validateProjectPolicy` 호출이 사용자 Rule로 매칭된다.
3. 결과에 사용자 Rule ID와 조건·호출·실패 위치 Evidence가 포함된다.
4. `CONTROL_FLOW_ONLY` 후보가 추측된 실행 조건이 아니라 `excludedBusinessRules`로 변환된다.
5. 잘못된 Rule과 올바른 Rule이 함께 있을 때 올바른 Rule은 유지된다.
6. YAML 문법 오류와 ID 충돌이 전체 분석을 중단시키지 않는다.
7. Rule별 평가·매칭·실패 횟수가 결정적으로 보고된다.

## 완료 기준

- [x] YAML 문자열과 UTF-8 파일에서 하나 이상의 Rule을 정의할 수 있다.
- [x] YAML Rule이 기존 `GraphRule`과 같은 입력·출력 계약으로 실행된다.
- [x] 기본 Rule Pack과 사용자 Rule Pack을 함께 적용한다.
- [x] 사용자 Rule 결과에 Rule ID와 Evidence가 포함된다.
- [x] 잘못된 Rule 하나가 다른 Rule을 제거하거나 분석을 실패시키지 않는다.
- [x] 로딩 수, Rule별 실행 지표, 로딩·실행 실패 사유를 확인할 수 있다.
- [x] 그래프에서 확인할 수 없는 실행 가능한 constraint를 생성하지 않는다.

## 비목표 및 후속 후보

- Rule 관리 UI, DB, 권한, 버전, 배포 및 롤백
- 자연어에서 YAML 자동 생성
- 기본 Rule 결과 덮어쓰기와 복잡한 충돌 해결
- Java Plugin 또는 사용자 스크립트 실행
- 완전한 Rule DSL
- 그래프 Evidence로 `targetPath`, 연산자, 기대값까지 증명할 수 있는 출력 DSL
- CLI 및 웹 UI의 Rule 파일 선택 기능

마지막 두 항목은 PoC 결과를 바탕으로 별도 Work Unit에서 설계한다. 실행 가능한 Validation Condition을 추가할 때는 YAML 값 자체가 아니라 그래프의 parameter origin, literal, enum constant 등과 연결되는 명시적 Evidence 계약이 먼저 필요하다.
