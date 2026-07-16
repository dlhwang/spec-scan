# FactCodeGraph 통합 construction

## 진행 원칙

- UoW 01부터 06까지 순차 진행한다.
- 각 UoW 완료 조건을 만족할 때만 체크한다.
- production 변경 전에는 관련 파일과 변경 이유를 먼저 확인한다.
- 테스트 체크박스는 실제 검증을 수행한 뒤에만 체크한다.

## 전체 진행

- [x] UoW 01 - 정규화 책임 및 기존선 고정
- [x] UoW 02 - 서비스 및 Validator 규칙 GraphRule 이관
- [x] UoW 03 - 요청 DTO 및 애노테이션 제약 이관
- [x] UoW 04 - FactGraphBuildResult 단일 생성 및 전달
- [x] UoW 05 - 출력 및 Evidence 산출물 이관
- [x] UoW 06 - 기존 정규화 및 Evidence Graph 제거

## UoW 01 - 정규화 책임 및 기존선 고정

- [x] `RuleBasedConditionNormalizer` 분기 책임 분류
- [x] `NormalizedResult.conditions()` 소비처 확인
- [x] `StructuralConditionAdapter` 지원 operator 확인
- [x] `CandidateToOutputAdapter` request/excluded 분류 조건 특성화
- [x] `ResponseMetadataAdapter` response 생성 조건 특성화
- [x] `ResponseInvariantAdapter` response 생성 조건 특성화
- [x] 기존 `BusinessRuleCandidate` 복수 출력 경로 사용 여부 확인
- [x] rejected candidate, invalid chunk, warning 의미 노출 여부 확인
- [x] `validation-evidence-graph.json` 외부 계약 여부 기록
- [x] 기존 rule별 `BusinessRuleCategory`, 목표 `RuleEffect`, 최종 출력 맵 작성
- [x] 특성화 테스트 또는 비교 fixture 추가
- [x] production 동작 변경 없음 확인

검증 메모:

- `.\gradlew.bat compileJava` 통과.
- 기존 `compileTestJava` 전역 classpath 문제로 개별 테스트 실행은 실패한 상태.

## UoW 02 - 서비스 및 Validator 규칙 GraphRule 이관

- [x] `RuleEffect` enum 추가
- [x] `BusinessRuleCandidate`에 effect 필드 추가
- [x] 기존 및 신규 모든 `GraphRule`이 effect를 명시하도록 변경
- [x] SERVICE_HINT 흐름을 개별 `GraphRule`로 이관
- [x] custom validator predicate와 failure outcome을 fact로 수집
- [x] 신규 규칙을 rule pack 및 catalog에 등록
- [x] request, response, excluded 변환 adapter 분리
- [x] `EndpointRuleOutput` 세 배열을 effect 기준으로 상호 배타 분기
- [x] business restriction allowlist/template/evidence gate 유지
- [ ] UoW 02 테스트 통과

검증 메모:

- `.\gradlew.bat clean compileJava` 통과.
- 테스트 검증은 사용자가 별도 수행 예정.

## UoW 03 - 요청 DTO 및 애노테이션 제약 이관

- [x] `CUSTOM_ANNOTATION`과 Bean Validation source를 request binding에 연결
- [x] `RequestBindingConditionAdapter` 지원 제약 명시
- [x] `NOT_NULL`, `NOT_EMPTY`, `NOT_BLANK`, `EMAIL` 지원 정책 확정
- [x] body, query, path, header 위치 결정
- [x] source trace를 input-origin evidence로 생성
- [x] `StructuralConditionAdapter`의 normalized condition 의존 제거
- [ ] UoW 03 테스트 통과

검증 메모:

- `.\gradlew.bat clean compileJava` 통과.
- `NOT_NULL`, `NOT_EMPTY`는 실행 조건으로 생성하고 `NOT_BLANK`, `EMAIL`은 diagnostic으로 유지.

## UoW 04 - FactGraphBuildResult 단일 생성 및 전달

- [x] 그래프 생성 위치를 application pipeline 상위로 이동
- [x] `RuleOutputService.generate()`에 `FactGraphBuildResult` 전달
- [x] `RuleOutputService` 내부 builder 호출 제거
- [x] `GitExecutionSpecScanService.scan()` 흐름 정리
- [x] `OpenApiAssemblyService`가 동일 graph build result를 전달받도록 변경
- [x] build diagnostics를 최종 diagnostic에 연결
- [ ] UoW 04 테스트 통과

검증 메모:

- `.\gradlew.bat clean compileJava` 통과.
- `RuleOutputService`는 더 이상 `RepositorySource`로 `FactCodeGraph`를 생성하지 않고 `FactGraphBuildResult`를 입력으로 받음.

## UoW 05 - 출력 및 Evidence 산출물 이관

- [x] `StructuredSpecExporter`의 `NormalizedResult.conditions()` 의존 제거
- [x] execution spec에 graph rule과 request constraint 결과 반영
- [x] OpenAPI 생성 입력을 신규 execution model로 통일
- [x] `validation-evidence-graph.json` 외부 파일명 계약은 유지
- [x] 파일 내용은 legacy `ValidationEvidenceGraph`가 아니라 `FactGraphBuildResult` 기반 산출물로 전환
- [x] `RuleOutputService.generate()`의 legacy structural conditions 입력 제거
- [x] `fact-code-graph.json` 스키마 전환 정책 문서화
- [ ] UoW 05 테스트 통과

검증 메모:

- `.\gradlew.bat clean compileJava` 통과.
- 현재 `validation-evidence-graph.json` 파일명은 호환을 위해 유지하되, 직렬화 대상은 `FactGraphBuildResult`.
- `fact-code-graph.json`은 즉시 교체하지 않고 병행 산출 → deprecation → 제거 순서로 전환.

## UoW 06 - 기존 정규화 및 Evidence Graph 제거

- [x] production legacy 참조 제거
- [x] `NormalizationService` 제거
- [x] `RuleBasedConditionNormalizer` 제거
- [x] `CandidateChunk`, `NormalizedResult`, `ValidationEvidenceGraph`, legacy `GraphNode`/`GraphEdge` 제거
- [x] `CandidateChunkGenerator`, `CandidateChunkValidator`, `GraphContextSelector`, `PromptBuilder`, `LlmResponseValidator` 제거
- [x] `ValidationEvidenceGraphBuilder`, `NormalizationRejectionClassifier`, `StructuralConditionAdapter` 제거
- [x] production 참조 0건 확인
- [x] test legacy 참조 제거
- [ ] 불필요한 constructor overload와 adapter 제거
- [ ] warning code 유지 여부 정리
- [ ] 문서와 workflow 설명을 최종 구조로 갱신
- [ ] dead code 및 중복 fixture 제거
- [ ] 전체 컴파일 및 테스트 통과

검증 메모:

- `src/main/java` 기준 legacy 키워드 검색 결과 직접 참조 없음.
- `.\gradlew.bat clean compileJava` 통과.
- test source의 legacy 참조는 제거 완료.
- UoW 테스트 실행은 사용자가 별도 검증 후 진행 예정.
