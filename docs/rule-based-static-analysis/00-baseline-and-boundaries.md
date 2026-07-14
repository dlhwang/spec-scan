# Work Unit 00: 기준선과 분석 경계 고정

## 목적

리팩터링 전에 현재 분석기의 동작, 알려진 오탐과 미탐, 초기 PoC의 지원 범위를 실행 가능한 계약으로 고정한다. 이 Unit에서는 production 분석 로직을 변경하지 않는다.

## 필요한 배경

현재 구현은 문자열 기반 `RuleClass` 분류와 도메인 고정 정규화를 포함한다. 이를 한 번에 제거하면 기존 기능의 의도된 동작과 과적합 동작을 구분하기 어렵다.

## 작업 범위

### 1. Characterisation test 작성

현재 결과를 다음 세 범주로 기록한다.

- 유지해야 할 동작
- 신규 구조에서 교정해야 할 오탐 또는 하드코딩
- 현재 미지원으로 인정할 동작

테스트 이름에 기대하는 미래 동작을 섞지 않는다. 현재 동작을 사실대로 표현한다.

### 2. 분석 지원 계약 작성

테스트 또는 코드 상수로 다음을 명시한다.

- 지원 Java 문법과 조건식
- 메서드 탐색 최대 깊이
- 타입 해석 성공 및 실패 정책
- source root와 dependency resolution 실패 정책
- 지원하는 branch outcome

### 3. 기준 데이터셋 구분

- `ddd-start2`: 기존 동작 회귀 데이터
- `RealEstate`: 교차 프로젝트 검증 데이터
- synthetic fixture: 구조별 양성, 음성, 경계 사례
- holdout fixture: Rule 구현 시 직접 참조하지 않는 최종 검증 데이터

## 주요 확인 대상

- `ValidationEvidenceGraphBuilder`
- `RuleBasedConditionNormalizer`
- `NormalizationServiceTest`
- `ValidationEvidenceGraphBuilderTest`
- `OpenApiPipelineRegressionTest`

## 산출물

- 현재 동작을 고정한 테스트
- 지원 범위와 제외 범위를 표현한 테스트 문서 또는 테스트 클래스 설명
- 알려진 하드코딩 목록
- baseline 결과 JSON 또는 assertion

## 완료 기준

- 기존 테스트가 모두 통과한다.
- `currentUser`, `HAS_CANCELLATION_PERMISSION`, 주문 상태 상수처럼 교정 대상인 결과가 명시돼 있다.
- 최소 하나의 false-positive fixture가 있다.
- 최소 하나의 type-resolution-failure fixture가 있다.
- holdout 데이터는 Rule 구현 테스트와 물리적으로 구분돼 있다.

## 비목표

- 새 그래프 모델 도입
- RuleClass 제거
- 출력 스키마 변경
- 탐지율 개선

## 위험과 대응

- 기존 출력을 모두 올바른 동작으로 고정하지 않는다. 각 assertion에 `preserve`, `replace`, `remove` 의도를 남긴다.
- 실제 프로젝트만 기준으로 사용하지 않는다. 이름을 바꾼 synthetic fixture를 반드시 포함한다.

