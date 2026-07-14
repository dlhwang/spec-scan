# Work Unit 03: Graph Rule 엔진

## 목적

사실 그래프에서 검증 후보를 찾고, 독립적인 Rule 구현을 적용해 0개 이상의 비즈니스 룰 후보를 생성하는 확장 구조를 만든다.

## 후보 생성과 의미 매칭 분리

Rule 미매칭만으로 일반 제어문과 새로운 비즈니스 룰을 구분할 수 없다. 따라서 두 단계를 둔다.

1. `ValidationCandidateDetector`: 실패 outcome과 연결된 조건을 후보로 생성
2. `GraphRule`: 후보 주변 그래프를 해석해 의미를 부여

## 제안 인터페이스

```java
public interface ValidationCandidateDetector {
    List<PredicateCandidate> detect(CodeGraph graph, MethodScope scope);
}

public interface GraphRule {
    String id();

    RuleLayer layer();

    List<BusinessRuleCandidate> match(
        CodeGraph graph,
        PredicateCandidate candidate
    );
}
```

`Optional`을 사용하지 않는다. 한 조건에서 복수 의미 후보가 생기거나, 한 메서드에서 같은 Rule이 여러 번 매칭될 수 있기 때문이다.

## Rule 계층

```java
enum RuleLayer {
    JAVA_LANGUAGE,
    JDK_IDIOM,
    SPRING,
    SPRING_DATA_JPA,
    PROJECT_EXTENSION
}
```

프레임워크 룰을 범용 Java 룰이라고 부르지 않는다. 엔진은 계층과 무관하고 Rule Pack 등록으로 확장한다.

## 매칭 결과 충돌 정책

동일 predicate에 여러 Rule이 매칭될 수 있다. 엔진은 결과를 임의로 하나 선택하지 않는다.

- 동일 `ruleId + evidence`는 중복 제거한다.
- 서로 다른 category가 매칭되면 후보를 유지하고 ambiguity diagnostic을 추가한다.
- 명시적인 precedence가 필요한 경우 Rule Pack 수준에서 선언한다.
- confidence만으로 자동 승자를 정하지 않는다.

## 타입 해석 fallback

우선순위는 다음과 같다.

1. fully qualified receiver type과 resolved method signature
2. 상속 또는 인터페이스 assignability
3. AST 구조, 인자 수, 분기 outcome
4. 제한된 이름 휴리스틱

3 또는 4를 사용하면 `ExtractionStatus.PARTIAL`과 관련 diagnostic을 남긴다. simple method name 하나만으로 의미를 확정하지 않는다.

## 실행기 책임

- Rule 등록 순서와 pack 활성화
- scope와 traversal budget 전달
- Rule별 실패 격리 및 diagnostic
- 결과 중복 제거
- 실행 통계 수집
- legacy 분석과의 병렬 실행 지원

## 변경 예상 파일

- 신규 candidate detector
- 신규 GraphRule SPI
- Rule registry 또는 constructor injection 구성
- extraction orchestrator
- 충돌 및 다중 매칭 단위 테스트

## 완료 기준

- 한 메서드의 동일 종류 검증 두 개를 모두 반환한다.
- Rule 추가 시 중앙 `if-else`, `switch`, RuleClass를 수정하지 않는다.
- 타입 해석 fallback이 결과와 diagnostic에 드러난다.
- 복수 Rule 충돌이 조용히 덮어써지지 않는다.
- Rule 예외 하나가 전체 분석을 중단시키지 않는다.
- 등록 Rule이 없는 후보는 `UNRESOLVED`로 보존된다.

## 비목표

- 외부 JSON/YAML Rule DSL
- 동적 plugin loading
- LLM 기반 Rule 선택
- domain taxonomy 자동 생성

