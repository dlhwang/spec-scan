# API 실행 모델 생성 워크플로 이해하기

이 워크플로는 **소스 코드에서 API의 동작 조건을 찾아 테스트 가능한 형태로 바꾼 뒤 `api-execution-model.json`으로 출력하는 과정**이다.

## 전체 흐름

```text
Controller AST 분석
  → ApiEndpoint 추출
  → endpoint별 FactCodeGraph 생성
  → PredicateCandidate 탐지
  → GraphRule 적용
  → BusinessRuleCandidate 생성
  → CandidateToOutputAdapter
  → requestPreconditions
  → responseAssertions
  → excludedBusinessRules
  → api-execution-model.json
```

## 단계별 한 문장 설명

| 단계 | 설명 |
|---|---|
| **Controller AST 분석** | 컨트롤러와 연결된 메서드의 코드를 구문 트리로 읽어 조건문, 메서드 호출, 반환, 예외 발생 등의 코드 구조를 파악한다. |
| **ApiEndpoint 추출** | 매핑 애너테이션과 메서드 선언에서 HTTP 메서드, URL, 요청 파라미터, 요청 본문 등 API의 입구 정보를 추출한다. |
| **endpoint별 FactCodeGraph 생성** | 각 endpoint에서 실행될 수 있는 코드를 따라가며 조건, 호출, 반환, 예외 사이의 관계를 사실 기반 그래프로 만든다. |
| **PredicateCandidate 탐지** | 그래프에서 실패 결과로 이어지는 조건이나 호출을 찾아 “API 실행을 제한하는 규칙일 가능성이 있는 구조”로 표시한다. |
| **GraphRule 적용** | 후보 주변의 코드 구조를 미리 정의된 규칙과 비교해 null 검사, 허용값 검사, 조회 실패, 권한 검사 등 조건의 의미를 판별한다. |
| **BusinessRuleCandidate 생성** | 규칙과 일치한 구조적 후보를 “name은 필수다”처럼 의미와 제약조건이 부여된 비즈니스 규칙 후보로 변환한다. |
| **CandidateToOutputAdapter** | 비즈니스 규칙 후보를 실제 API 테스트에서 실행 가능한 조건, 실행 불가능한 제외 규칙, 진단 정보로 분류해 출력 모델에 맞게 변환한다. |
| **requestPreconditions** | API 호출 전에 요청 파라미터나 본문이 만족해야 하고 테스트 입력으로 직접 구성할 수 있는 조건을 저장한다. |
| **responseAssertions** | API 호출 후 상태 코드, 헤더, 응답 본문이 기대한 결과와 일치하는지 검사할 조건을 저장한다. |
| **excludedBusinessRules** | 코드에서 확인됐지만 DB 상태나 로그인 사용자처럼 요청값만으로 준비·검증할 수 없는 규칙을 근거와 함께 보존한다. |
| **api-execution-model.json** | endpoint별 요청 전제조건, 응답 검증 조건, 제외된 비즈니스 규칙과 분석 근거를 모은 최종 API 실행·테스트 모델이다. |

## PredicateCandidate는 무엇을 탐지하는가?

`PredicateCandidate`는 모든 `if`문을 무조건 수집하는 것이 아니라, **요청 거부나 실행 실패로 이어지는 조건 또는 호출**을 탐지한다.

예를 들어 다음 코드가 있다고 가정한다.

```java
if (request.getName() == null) {
    throw new IllegalArgumentException();
}
```

그래프에서는 대략 다음 관계가 만들어진다.

```text
조건: request.name == null
              │
              └── 실패 결과: IllegalArgumentException 발생
```

탐지기는 이 관계를 보고 `request.name == null`을 `PredicateCandidate`로 만든다.

명시적인 `if`문이 없어도 실패를 내포한 호출은 후보가 될 수 있다.

```java
repository.findById(id).orElseThrow();
```

이 경우에는 `orElseThrow()` 호출이 “조회 결과가 없으면 실패한다”는 조건을 나타내므로 구조적 후보가 된다.

### 왜 별도의 후보 탐지 단계가 필요한가?

`FactCodeGraph`에는 메서드 호출, 값, 반환 등 많은 코드 사실이 들어 있으므로, 그중 **실패를 발생시키는 조건만 먼저 추려 후속 규칙이 분석할 대상을 좁히기 위해서**다.

## PredicateCandidate와 BusinessRuleCandidate의 차이

| 구분 | 답하는 질문 | 예시 |
|---|---|---|
| **PredicateCandidate** | “실패를 만드는 조건이 존재하는가?” | `request.name == null`일 때 예외가 발생한다. |
| **BusinessRuleCandidate** | “그 조건은 업무적으로 무엇을 의미하는가?” | 요청의 `name`은 null이면 안 된다. |

즉, 두 단계의 관계는 다음과 같다.

```text
PredicateCandidate
  코드에서 “실패와 연결된 수상한 조건”을 발견
                ↓ GraphRule로 의미 해석
BusinessRuleCandidate
  그 조건을 “필수값, 허용값, 조회 존재 여부, 권한” 등의 규칙으로 표현
```

## 최종 출력 분류 예시

### requestPreconditions

요청값만으로 준비할 수 있는 실행 조건이다.

```text
BODY $.name       NOT_NULL
BODY $.status     IN [READY, ACTIVE]
PATH $.orderId    GREATER_THAN 0
```

### responseAssertions

API 실행 결과에서 직접 확인할 수 있는 검증 조건이다.

```text
STATUS $          EQUALS 200
BODY $.status     EQUALS ACTIVE
```

### excludedBusinessRules

규칙은 확인됐지만 외부 실행 상태가 필요해 요청 조건이나 응답 검증으로 바로 만들 수 없는 항목이다.

```java
repository.findById(id).orElseThrow();
```

위 코드는 “해당 ID의 데이터가 DB에 존재해야 한다”는 규칙이지만, 요청의 `id` 값만으로 실제 DB 존재 여부를 보장할 수 없으므로 제외 규칙으로 보존될 수 있다.

## 한 줄 요약

```text
코드 구조 분석 → 실패 조건 발견 → 조건의 의미 해석
              → 테스트 가능한 조건과 불가능한 조건 분류 → JSON 출력
```
