# UOW-04 Code Generation Plan

`UOW-04: Candidate Chunk and Normalization` 유닛에 대한 코드 생성 계획입니다. 비기능 설계 문서 생략 지침(NFR Requirements)이 개발 유닛 생략으로 오인되어 누락되었던 **기능 유닛 UOW-04**를 추가 구현하여, 유효성 검증 후보군(`ValidationCandidate`)을 작은 입력 단위인 청크(`CandidateChunk`)로 쪼개고, 결함 필터링 및 프롬프트 조립을 거쳐 안전하게 정규화(`ApiCondition`)하는 중간 파이프라인을 구축합니다.

## User Review Required

> [!IMPORTANT]
> **핵심 아키텍처 및 구현 결정 사항**
> 1. **청크 분할 및 무효 필터링 (S-06)**:
>    - `CandidateChunkGenerator`: 수집된 `ValidationCandidate`들을 API 엔드포인트별, 소스 소스타입별(어노테이션, Validator, 서비스) 경계를 유지하며 작은 `CandidateChunk`로 분할합니다.
>    - `CandidateChunkValidator`: `candidateId` 충돌, `sourceTrace` 누락, `evidenceSnippet` 공백 등의 조건을 검사하여 무효(Invalid) 청크는 사전에 필터링하고 제외합니다.
> 2. **프롬프트 조립 및 응답 스키마 검증 (S-07)**:
>    - `PromptBuilder`: 분할된 청크 정보를 바탕으로 LLM 전송용 정규화 프롬프트(JSON Schema 명세 및 원본 스니펫 포함)를 조립합니다.
>    - `LlmResponseValidator`: 시뮬레이션된 LLM JSON 응답을 파싱하고, 필수 항목(`operator`, `confidence`) 누락 시 retry 혹은 rejected로 격리 처리합니다.
> 3. **UOW-05 연동 리팩토링**:
>    - UOW-05 구현에 임시로 단순 구현되어 있던 `LlmNormalizationService`를 UOW-04의 완성도 높은 `NormalizationService` 및 `CandidateChunk` 아키텍처로 대체 연동하고, UOW-05는 오직 OpenAPI Spec 조립 및 저장 기능에만 집중하게 분리 리팩토링합니다.

## Proposed Changes

### [Component: Domain Models]
청크 및 검증 정보를 표현하는 모델을 추가/수정합니다.

#### [NEW] [CandidateChunk.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/CandidateChunk.java)
- LLM에 전달하기 위한 논리적 정규화 작업 단위:
  - `chunkId` (고유 식별자)
  - `endpointPath` (관련 API 경로)
  - `sourceType` (ANNOTATION, VALIDATOR, SERVICE_HINT)
  - `candidates` (`List<ValidationCandidate>`)

#### [MODIFY] [NormalizedResult.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/NormalizedResult.java)
- 무효 필터링된 청크 목록 정보도 함께 반환하도록 수정:
  - `conditions` (`List<ApiCondition>`)
  - `rejected` (`List<ValidationCandidate>`)
  - `invalidChunks` (`List<CandidateChunk>`)

---

### [Component: Chunkers & Validators]

#### [NEW] [CandidateChunkGenerator.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/CandidateChunkGenerator.java)
- API Endpoint별, 후보 소스 타입별로 엮어 `List<CandidateChunk>`로 나누어주는 컴포넌트

#### [NEW] [CandidateChunkValidator.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/CandidateChunkValidator.java)
- 청크 내부 후보들의 무결성 검증:
  - `candidateId` 중복 검사
  - `sourceTrace` 존재 여부 검사
  - `evidenceSnippet` 유효성 검사

#### [NEW] [PromptBuilder.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/PromptBuilder.java)
- 청크 정보를 JSON 기반 프롬프트 템플릿으로 조립하는 빌더

#### [NEW] [LlmResponseValidator.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/LlmResponseValidator.java)
- LLM에서 렌더링된 응답 JSON의 스키마 제약사항 검사

---

### [Component: Application Services]

#### [NEW] [NormalizationService.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/NormalizationService.java)
- `NormalizedResult normalize(List<ValidationCandidate> candidates, List<ApiEndpoint> endpoints) throws IngestionException;`
- **핵심 흐름**:
  1. `CandidateChunkGenerator`로 청크 분할
  2. `CandidateChunkValidator`로 무효 청크 걸러내고 `invalidChunks`에 분류
  3. 통과된 유효 청크들에 대해 `PromptBuilder` 가동 및 LLM API(또는 Mock 시뮬레이터) 호출
  4. `LlmResponseValidator`로 응답 검증 후 정상 조건은 `ApiCondition`으로 승격, 오류는 `rejected`로 분류

#### [MODIFY] [LlmNormalizationService.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/LlmNormalizationService.java)
- [DELETE] 이 임시 클래스는 더 견고하고 구조화된 `NormalizationService`로 교체되므로 삭제합니다.

#### [MODIFY] [OpenApiAssemblyService.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/OpenApiAssemblyService.java)
- `LlmNormalizationService` 대신 신규 `NormalizationService`를 사용하여 청크 기반 정규화 결과를 수집하도록 연동을 수정합니다.

---

### [Component: Test Suite]

#### [NEW] [NormalizationServiceTest.java](file:///d:/workspace/auto-oas/src/test/java/io/atworks/specscan/analysis/NormalizationServiceTest.java)
- **청크 분할 테스트**: 후보들이 종류별/엔드포인트별 올바른 Chunk로 쪼개지는지 검증
- **무효 필터링 테스트**: `candidateId` 누락이나 trace가 없는 후보 청크가 invalid 처리로 빠지는지 검증
- **LLM 파서 및 Schema 검증 테스트**: 스키마 위반 JSON 응답 입력 시 rejected 격리가 정상적으로 동작하는지 검증

---

## Verification Plan

### Automated Tests
```powershell
./gradlew test
```
