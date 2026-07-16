# UOW-9-1: PredicateCandidate Seed 확장

## Goal (목표)
메서드 바디 내에 명시적인 `RETURN` 또는 `THROW` 문이 없더라도, `Objects.requireNonNull()`, `Preconditions.checkArgument()` 등과 같이 검증 예외를 유발하는 Void 타입의 Guard Method 호출(Validation Sink)을 감지해 `PredicateCandidate` 시드로 수집한다.

## 1. 문제 증거
현재 `PredicateCandidate` 생성 로직은 그래프의 시작점을 `RETURN`과 `THROW` 노드로만 제한하고 있을 가능성이 큽니다.
이 때문에 다음과 같은 검증 패턴 코드는 메서드 내에 직접적인 return이나 throw 문이 보이지 않으므로 Candidate 단계에서 완전히 누락됩니다:
```java
Objects.requireNonNull(userId, "userId must not be null");
Preconditions.checkArgument(age >= 0, "age must be positive");
```
이러한 Void 타입의 Validation Sink 또한 엄연히 값 검증 역할을 하므로, 분석 대상 후보에 포함되어야 합니다.

## 2. 지원 대상 코드 패턴
```java
Objects.requireNonNull(request.getName());
Assert.hasText(request.getEmail(), "email empty");
```

## 3. 현재 그래프
* `Objects.requireNonNull` 호출 노드는 생성될 수 있으나, 이 자체가 `RETURN` / `THROW`에 걸리지 않으므로 `PredicateCandidate`로 연결 및 추출되지 않음.

## 4. 기대 그래프
```text
METHOD_CALL(requireNonNull) -> (PredicateCandidate 시드로 등록 가능하도록 Candidate Seed 기준 정보 확장)
  ├─ RECEIVER → Objects
  └─ ARGUMENT → FIELD_ACCESS(request.getName)
```

## 5. 수정 책임 클래스
* `PredicateCandidate` 생성기 클래스 (또는 GraphRule 입력 처리 레이어)
* [FactMethodVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java) (필요시 Node Metadata에 Guard Method 정보 마킹)

## 6. 비대상 범위
* 모든 메서드 호출을 시드로 올릴 경우 분석 결과가 무의미하게 비대해지므로, `Objects`, `Preconditions`, `Assert` 등 지정된 검증 API 패키지/클래스의 시그니처만 필터링하여 시드로 수집합니다.

## 7. 테스트 케이스
* `Objects.requireNonNull` 호출만으로 구성된 메서드 소스를 분석하여, 최종적으로 `PredicateCandidate` 목록에 해당 검증 항목이 추출되는지 확인.

## 8. 완료 기준
* 대표적인 검증 라이브러리의 Guard Method 호출이 `PredicateCandidate` 시드로 인식됩니다.
* 후보군 추출 건수가 증가해도 정밀도(Precision)가 깨지지 않도록 필터 룰이 동작합니다.

## 9. 성능 및 호환성 영향
* 분석 대상인 Candidate 수가 증가함에 따라 후속 GraphRule 검사 성능에 영향이 가므로 필터 리스트 관리가 핵심적입니다.
