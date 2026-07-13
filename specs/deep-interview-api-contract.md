# Deep Interview Specification: API Contract Alignment

## 의사결정 사항 (Decisions)
- **Decision 1**: 기존 `validation-condition-improvement-plan.md` 파일은 그대로 유지하되, 내부 내용 전체를 새로운 검증 어설션 모델 정렬 계획으로 대체 및 개정한다.
- **Decision 2**: `AUTH`(예: currentUser), `RESOURCE`(예: order.state), 리포지토리 존재성, Optimistic Lock 등은 최종 실행 계약 모델(`requestPreconditions`, `responseAssertions`)에서 배제하되, 정적 분석 및 후보 수집 단계(evidence) 수준에서는 흔적으로 보존하여 추후 분석 가치를 살린다.
- **Decision 3**: `RequestPrecondition` 및 `ResponseAssertion`은 Java 소스 상에서 기존 `ApiCondition` 클래스의 필드 구조를 최대한 재활용하여 구현하며, `conditionType` (또는 이와 동등한 필드)을 활용해 런타임/출력 시 개념적으로 분리해 조립한다.

## 하드 제약 조건 (Constraints)
- **Constraint 1**: 최종 JSON 조립(`assembly`) 및 매핑 단계에서 `AUTH`, `RESOURCE`, DB 존재 여부, optimistic lock 등과 결부되는 조건들은 완전히 필터링되어 최종 `requestPreconditions`와 `responseAssertions`에서 제외되어야 한다.
- **Constraint 2**: 모든 변경 사항은 기존 데이터 포맷 및 인터페이스와의 하위 호환성을 최대한 유지할 수 있는 `ApiCondition` 재활용 설계 규칙을 준수해야 한다.
