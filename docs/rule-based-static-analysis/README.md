# Rule-Based Java Static Analysis 개선 계획

## 문서 목적

이 문서 묶음은 현재 `auto-oas` 분석기를 특정 예제 저장소에 맞춘 휴리스틱 분석기에서, 프로젝트와 도메인 이름에 독립적인 구조 기반 Java 정적분석 PoC로 개선하기 위한 개발 계획이다.

각 Work Unit은 독립적인 작업 지시서다. 작업자는 이 README와 자신이 담당한 Unit만 읽고 시작할 수 있으며, 세부 설계가 필요할 때만 선행 Unit을 참조한다.

## 목표

이번 개선에서 말하는 범용성은 모든 Java 코드와 프레임워크를 완전하게 해석한다는 뜻이 아니다.

> 핵심 그래프 모델과 분석 절차가 특정 프로젝트의 클래스명, 메서드명, 변수명, Enum 값에 의존하지 않고, 지원하지 못한 코드를 잘못 해석하지 않으며 그 이유를 근거와 함께 보고하는 것을 의미한다.

최종 분석 흐름은 다음과 같다.

```text
Java source and type information
  -> Fact Code Graph
  -> Validation Candidate Discovery
  -> Graph Rule Matching
  -> Business Rule Candidate
  -> API Condition / Export Adapter
```

## 핵심 설계 원칙

1. Fact Code Graph에는 소스와 타입 해석에서 직접 관찰한 사실만 저장한다.
2. 비즈니스 의미는 Graph Rule 단계에서만 부여한다.
3. 추출 성공 여부, 의미 해석 여부, 요청 필드 연결 여부를 서로 다른 상태로 관리한다.
4. Graph Rule 하나는 한 메서드에서 여러 후보를 반환할 수 있다.
5. 소스에서 확인할 수 없는 `targetPath`, `operator`, `expected`를 생성하지 않는다.
6. 프레임워크 관용구는 범용 Java 규칙과 분리된 Rule Pack으로 관리한다.
7. 미지원 및 미해석 결과를 오탐으로 바꾸지 않는다.
8. 새 Rule 추가에 기존 중앙 분류 Enum 수정이 필수가 되어서는 안 된다.

## 지원 경계

초기 PoC의 기본 범위는 다음과 같다.

- JavaParser가 파싱할 수 있는 Java 소스
- 메서드 내부의 `if` 조건과 직접 연결된 `throw` 또는 `return`
- `==`, `!=`, `<`, `<=`, `>`, `>=`, `&&`, `||`, `!`
- 메서드 호출, 인자, 필드 접근, Enum 상수, 단순 지역 변수 별칭
- 설정 가능한 제한 깊이의 메서드 호출 탐색
- 타입 해석 성공 시 resolved signature 사용
- 타입 해석 실패 시 명시적인 degraded 상태와 제한적 휴리스틱 사용

초기 범위에서 제외한다.

- 전체 프로그램 수준의 완전한 interprocedural data flow
- SSA 및 완전한 CFG
- 동적 디스패치와 reflection의 완전 해석
- 반복문과 stream 내부의 복합 검증
- helper method를 통과하는 임의 깊이의 값 추적
- 모든 프레임워크의 의미 모델
- 자동 실행 테스트의 완전 생성

## Work Unit 목록

| 순서 | Unit | 결과물 | 선행 Unit |
|---|---|---|---|
| 0 | [00 기준선과 경계](00-baseline-and-boundaries.md) | 현재 동작 기준선, 지원 계약 | 없음 |
| 1 | [01 사실 코드 그래프](01-fact-code-graph.md) | 의미가 섞이지 않은 그래프 | Unit 0 |
| 2 | [02 후보와 해석 모델](02-candidate-and-resolution-model.md) | 후보, 상태, Evidence 모델 | Unit 0 |
| 3 | [03 Graph Rule 엔진](03-graph-rule-engine.md) | 다중 매칭 Rule SPI와 실행기 | Unit 1, 2 |
| 4 | [04 초기 Rule Pack](04-initial-rule-pack.md) | Java/JDK/Spring/JPA 초기 룰 | Unit 3 |
| 5 | [05 출력 마이그레이션](05-normalization-and-output-migration.md) | 기존 출력 어댑터와 하드코딩 제거 | Unit 2, 3, 4 |
| 6 | [06 검증과 평가](06-validation-and-evaluation.md) | fixture, holdout, 지표 | Unit 0부터 지속 |
| 7 | [07 전달 순서](07-delivery-sequence.md) | 통합 순서와 완료 게이트 | 전체 |

## 작업 규칙

- 각 Unit은 별도 PR 또는 별도 커밋 단위로 완료할 수 있어야 한다.
- 작업 중 발견한 추가 범위는 현재 Unit에 조용히 포함하지 않고 후속 항목으로 기록한다.
- 테스트 fixture의 이름과 도메인 용어를 production rule에 복사하지 않는다.
- 신규 구조가 검증되기 전까지 legacy 경로를 제거하지 않는다.
- 두 경로의 결과를 비교할 수 있는 기간을 둔다.

## 최종 성공 기준

- 특정 예제의 `currentUser`, 주문 상태 값, 취소 권한 연산자가 production 코드에서 제거된다.
- 동일 구조의 검증이 변수명과 도메인명이 바뀐 fixture에서도 탐지된다.
- 같은 Rule이 둘 이상의 독립 프로젝트 또는 fixture에서 재사용된다.
- 모든 결과가 소스 위치와 그래프 Evidence를 포함한다.
- 미해석과 미지원이 잘못된 비즈니스 의미로 출력되지 않는다.
- 새 Graph Rule 추가 시 기존 Rule 구현과 중앙 분류 분기를 수정하지 않는다.

