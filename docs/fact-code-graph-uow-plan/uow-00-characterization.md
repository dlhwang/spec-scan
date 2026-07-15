# UoW 00 — 현재 파이프라인 특성화

## 목적

변경 전 동작을 테스트로 고정하고 `FactCodeGraph`와 `ValidationEvidenceGraph`의 producer와 consumer를 확인한다.

## 작업 범위

- scan 요청부터 execution spec 출력까지 호출 흐름 기록
- 두 그래프의 생성·소비 지점 식별
- RealEstate 6개 API 현재 결과를 baseline fixture로 저장
- 현재 생성되는 Operator와 graph evidence 없는 조건 수집

## 주요 대상

- `OpenApiAssemblyService`
- `ExecutionSpecExporter`
- `RuleBasedConditionNormalizer`
- `ValidationEvidenceGraphBuilder`
- `DefaultFactCodeGraphBuilder`
- output adapter 계열

## 완료 조건

- 두 그래프의 책임과 연결 관계가 문서 또는 테스트 이름으로 드러난다.
- RealEstate 결과가 변경 전 baseline으로 재현된다.
- 반복 실행 결과의 결정성을 검증한다.

## 테스트

- `RealEstateCurrentBehaviorCharacterizationTest`
- 동일 입력 반복 실행 비교
- operation별 Operator 및 warning snapshot

