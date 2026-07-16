# UoW 04 — FactGraphBuildResult 단일 생성 및 전달

## 목적

분석 요청당 `FactGraphBuildResult`를 한 번만 생성하고 모든 graph rule 출력에서 같은 결과를 재사용함.

## 선행 조건

- UoW 02 완료
- UoW 03 완료

## 작업 범위

- 그래프 생성 위치를 application pipeline 상위로 이동
- `RuleOutputService.generate()`에 `FactGraphBuildResult` 전달
- `RuleOutputService` 내부 builder 호출 제거
- `GitExecutionSpecScanService.scan()`과 `scanWithArtifacts()` 흐름 정렬
- `OpenApiAssemblyService`가 동일 graph build 결과를 전달받도록 변경
- build diagnostics를 최종 warning 또는 diagnostic에 연결

## 목표 API 예시

```java
FactGraphBuildResult factGraphs = factGraphService.build(scanResult, repositorySource);
Map<String, EndpointRuleOutput> outputs = ruleOutputService.generate(
    scanResult,
    factGraphs,
    requestConstraints
);
```

실제 시그니처는 구현 시 확정하되 `RuleOutputService`가 저장소에서 그래프를 다시 만들지 않아야 함.

## 예상 영향 파일

- `GitExecutionSpecScanService.java`
- `analysis/application/OpenApiAssemblyService.java`
- `analysis/application/RuleOutputService.java`
- 필요 시 단순 graph build application service

## 테스트

- 분석 요청당 builder 호출 1회
- 모든 endpoint graph가 동일 build result에서 선택됨
- graph가 없는 endpoint의 빈 출력 및 diagnostic
- scan과 scanWithArtifacts 결과 동등성
- 임시 작업공간 정리 회귀

## 완료 조건

- production 코드에서 그래프 생성 책임이 한 곳에만 존재함.
- `RuleOutputService`가 `RepositorySource`를 이용해 그래프를 생성하지 않음.
- build diagnostics가 손실되지 않음.
- 전체 관련 통합 테스트가 통과함.

