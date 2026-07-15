# UoW 03 — 요청 DTO 및 애너테이션 제약 이전

## 목적

그래프 실행 흐름과 무관한 요청 스키마 제약을 request binding 경로에서 직접 생성함.

## 선행 조건

- UoW 01 완료

## 작업 범위

- `CUSTOM_ANNOTATION`과 Bean Validation 제약의 source를 request binding에 연결
- `RequestBindingConditionAdapter` 지원 제약을 명시
- `NOT_NULL`, `NOT_EMPTY`, `NOT_BLANK`, `EMAIL` 지원 정책 확정
- body, query, path, header 위치 결정
- source trace와 input-origin evidence 생성
- `StructuralConditionAdapter`의 normalized condition 의존 제거

## 구현 원칙

- DTO 제약을 `FactCodeGraph`에 억지로 포함하지 않음.
- annotation 이름과 속성은 AST 또는 기존 direct condition에서 읽음.
- 지원하지 않는 custom annotation 의미를 추정하지 않음.
- 실행 가능한 operator allowlist를 준수함.
- `NOT_BLANK`, `EMAIL`처럼 실행 모델이 지원하지 않는 의미는 operator 확장 승인 없이는 유사 조건으로 변환하지 않음.

## 예상 영향 파일

- `analysis/support/output/RequestBindingConditionAdapter.java`
- `analysis/support/output/StructuralConditionAdapter.java`
- `analysis/application/ValidationExtractionService.java`
- request binding 및 annotation extractor 관련 파일

## 테스트

- body DTO의 `@NotNull`, `@NotEmpty`
- query 및 path parameter 제약
- 중첩 DTO target path
- 동일 제약 중복 제거
- source trace와 evidence role 보존
- unsupported annotation의 diagnostic 또는 무출력

## 완료 조건

- DTO 및 애너테이션 제약이 `NormalizationService` 없이 생성됨.
- 출력 위치와 target path가 기존 결과와 동등함.
- 모든 출력에 request binding 또는 annotation source evidence가 있음.
- `StructuralConditionAdapter`가 `List<ApiCondition>`을 요구하지 않음.

