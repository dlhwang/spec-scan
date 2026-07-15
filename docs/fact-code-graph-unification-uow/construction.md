# FactCodeGraph 통합 construction

## 진행 원칙

- UoW는 01부터 06까지 순차 진행함.
- 각 UoW 완료 조건을 만족한 뒤 체크함.
- production 변경 전에는 관련 파일과 변경 이유를 먼저 확인함.
- 레거시 제거는 신규 경로의 테스트와 소비처 이전이 끝난 뒤 수행함.

## 전체 진행

- [x] UoW 01 - 정규화 책임 및 기준선 고정
- [ ] UoW 02 - 서비스 및 Validator 규칙 GraphRule 이전
- [ ] UoW 03 - 요청 DTO 및 애너테이션 제약 이전
- [ ] UoW 04 - FactGraphBuildResult 단일 생성 및 전달
- [ ] UoW 05 - 출력 및 Evidence 산출물 이전
- [ ] UoW 06 - 기존 정규화 및 Evidence Graph 제거

## UoW 01 - 정규화 책임 및 기준선 고정

- [x] `RuleBasedConditionNormalizer` 분기 책임 분류
- [x] `NormalizedResult.conditions()` 소비처 확인
- [x] `StructuralConditionAdapter` 지원 operator 확인
- [x] `CandidateToOutputAdapter` request/excluded 분류 조건 특성화
- [x] `ResponseMetadataAdapter` response 생성 조건 특성화
- [x] `ResponseInvariantAdapter` response 생성 조건 특성화
- [x] 기존 `BusinessRuleCandidate` 복수 출력 경로 재사용 사례 확인
- [x] rejected candidate, invalid chunk, warning 외부 노출 여부 확인
- [x] `validation-evidence-graph.json` 외부 계약 여부 기록
- [x] 기존 rule별 `BusinessRuleCategory`, 목표 `RuleEffect`, 최종 출력 표 작성
- [x] 특성화 테스트 또는 비교 fixture 추가
- [x] production 동작 변화 없음 확인

검증 메모:

- `.\gradlew.bat compileJava` 통과.
- `.\gradlew.bat test --tests ...`는 기존 `compileTestJava` 전역 import 오류로 실패함.

## UoW 02 - 서비스 및 Validator 규칙 GraphRule 이전

- [x] `RuleEffect` enum 추가
- [x] `BusinessRuleCandidate`에 effect 필드 추가
- [x] 기존 및 신규 모든 `GraphRule`이 effect를 명시하도록 변경
- [x] SERVICE_HINT 의미를 개별 `GraphRule`로 이전
- [x] custom validator predicate와 failure outcome을 fact로 수집
- [x] 신규 규칙을 rule pack 및 catalog에 등록
- [x] request, response, excluded 변환 adapter 분리
- [x] `EndpointRuleOutput` 세 배열을 effect 기준으로 상호 배타 분기
- [x] business restriction allowlist/template/evidence gate 유지
- [ ] UoW 02 테스트 통과

검증 메모:

- `.\gradlew.bat clean compileJava` 통과.
- `CandidateToOutputAdapterTest`는 UoW 02 목표에 맞게 effect 기반 expectation으로 갱신함.
- `.\gradlew.bat test --tests "io.atworks.specscan.analysis.output.CandidateToOutputAdapterTest"`는 기존 `compileTestJava` 전역 classpath 오류로 실패함.
- `DefaultValidationCandidateDetector`와 `FactMethodVisitor`가 predicate 및 `THEN_OUTCOME`/`ELSE_OUTCOME` failure evidence를 이미 수집하고 있어 별도 fact builder 변경은 하지 않음.

## UoW 03 - 요청 DTO 및 애너테이션 제약 이전

- [x] `CUSTOM_ANNOTATION`과 Bean Validation source를 request binding에 연결
- [x] `RequestBindingConditionAdapter` 지원 제약 명시
- [x] `NOT_NULL`, `NOT_EMPTY`, `NOT_BLANK`, `EMAIL` 지원 정책 확정
- [x] body, query, path, header 위치 결정
- [x] source trace와 input-origin evidence 생성
- [x] `StructuralConditionAdapter`의 normalized condition 의존 제거
- [ ] UoW 03 테스트 통과

검증 메모:

- `.\gradlew.bat clean compileJava` 통과.
- annotation direct condition은 `RequestBindingConditionAdapter`에서 처리함.
- `NOT_NULL`, `NOT_EMPTY`만 실행 조건으로 생성하고 `NOT_BLANK`, `EMAIL`은 diagnostic으로 남김.
- `StructuralConditionAdapter`에는 normalized structural conditions만 전달함.

## UoW 04 - FactGraphBuildResult 단일 생성 및 전달

- [x] 그래프 생성 위치를 application pipeline 상위로 이동
- [x] `RuleOutputService.generate()`에 `FactGraphBuildResult` 전달
- [x] `RuleOutputService` 내부 builder 호출 제거
- [x] `GitExecutionSpecScanService.scan()`과 `scanWithArtifacts()` 흐름 정렬
- [x] `OpenApiAssemblyService`가 동일 graph build result를 전달받도록 변경
- [x] build diagnostics를 최종 warning 또는 diagnostic에 연결
- [ ] UoW 04 테스트 통과

검증 메모:

- `.\gradlew.bat clean compileJava` 통과.
- `RuleOutputService`는 더 이상 `RepositorySource`로 `FactCodeGraph`를 생성하지 않고 `FactGraphBuildResult`를 입력으로 받음.
- graph build diagnostic은 endpoint rule output diagnostic으로 투영함.

## UoW 05 - 출력 및 Evidence 산출물 이전

- [x] `StructuredSpecExporter`의 `NormalizedResult.conditions()` 의존 제거
- [x] execution spec에 graph rule과 request constraint 결과 투영
- [ ] OpenAPI 생성 입력을 신규 execution model로 통일
- [ ] `validation-evidence-graph.json` 외부 계약 정책 반영
- [ ] 계약 유지 시 `FactGraphBuildResult` 기반 호환 exporter 구현
- [ ] 계약 폐기 시 `fact-code-graph.json` 스키마와 전환 정책 문서화
- [ ] UoW 05 테스트 통과

검증 메모:

- `.\gradlew.bat clean compileJava` 통과.
- `StructuredSpecExporter`는 `EndpointRuleOutput` 기반으로 value validation과 request body template을 생성함.
- `RuleOutputService`는 아직 legacy structural conditions 입력을 받으므로 UoW 05 전체 완료는 아님.

## UoW 06 - 기존 정규화 및 Evidence Graph 제거

- [ ] production 및 test 레거시 참조 제거
- [ ] 불필요한 constructor overload와 adapter 제거
- [ ] 레거시 warning code 유지 여부 정리
- [ ] 문서와 workflow 설명을 최종 구조로 갱신
- [ ] dead code 및 중복 fixture 제거
- [ ] 제거 대상 심볼 production 참조 0건 확인
- [ ] 전체 컴파일 및 테스트 통과
