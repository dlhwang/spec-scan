# UOW-05 Code Generation Plan

`UOW-05: OpenAPI Document Assembly` 유닛에 대한 코드 생성 계획입니다. 본 계획에 따라 수집된 유효성 검사 후보군(`ValidationCandidate`)을 정규화(`ApiCondition`)하고, 이를 정적 분석 스캔 결과(`StaticScanResult`)와 조립하여 규격에 맞는 최종 OpenAPI 3.0 YAML 문서로 조립 및 저장하는 엔진을 구현합니다.

## User Review Required

> [!IMPORTANT]
> **핵심 아키텍처 및 구현 결정 사항**
> 1. **LLM Normalization 시뮬레이션 (Mocking & Parser)**:
>    - PoC 검증의 예측 가능성을 위해, 실제 LLM API 호출부를 유연하게 대체할 수 있는 `LlmNormalizationService` 구조를 잡고, 사전에 정의된 규칙 매핑 및 Mocking 메커니즘을 두어 테스트와 로직을 완벽하게 검증합니다.
>    - LLM 응답 포맷 검증(Schema validation)을 수행하여 필수 값(`operator`, `confidence`)이 누락되거나 사유(`llmReason`)가 없는 경우, 또는 Evidence 근거가 부적절한 경우 Rejected 상태로 격리합니다.
> 2. **OpenAPI 3.0 스펙 조립 (Assembly)**:
>    - `paths`, `components/schemas` 레이아웃을 생성합니다.
>    - DTO 필드에 해당하는 OpenAPI schema 필드에 `required` 리스트를 동적으로 주입하고, `minimum`, `maximum`, `pattern` 등의 검증 제약 조건을 수집된 `ApiCondition` 및 `ApiConditionDraft` 정보로부터 주입합니다.
> 3. **부분 실패(Partial Failure) 격리**:
>    - 일부 엔드포인트 조립이 오류로 실패하더라도, 전체 파일 저장을 롤백하지 않고 성공한 엔드포인트들만으로 OpenAPI 문서를 정상 빌드하고 실패 원인을 warnings 리포트에 별도 기록합니다.

## Proposed Changes

### [Component: Domain Models]
정규화 및 조립 결과 모델을 정의합니다.

#### [NEW] [ApiCondition.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/ApiCondition.java)
- LLM에 의해 최종 정규화 및 수용된 검증 사양 모델:
  - `targetPath` (필드 경로)
  - `operator` (NOT_NULL, SIZE, PATTERN 등)
  - `expected` (속성값)
  - `evidence` (코드 스니펫 근거)
  - `confidence` (신뢰도)
  - `llmReason` (정규화 판단 사유)
  - `sourceTrace` (`SourceTrace`)

#### [NEW] [NormalizedResult.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/NormalizedResult.java)
- 정규화 연산의 반환 Aggregate:
  - `conditions` (`List<ApiCondition>`)
  - `rejected` (`List<ValidationCandidate>`)

---

### [Component: Services & Assemblers]

#### [NEW] [LlmNormalizationService.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/LlmNormalizationService.java)
- `ValidationCandidate`들을 분석하여 정규화된 `ApiCondition` 목록으로 변환:
  - 어노테이션이나 예외 스니펫의 정규표현식/값을 정규화하여 표준 Operator로 승격
  - 사유(`llmReason`) 및 정합성 검증 규칙(Schema validation)을 검사해 부적합한 후보는 `rejected`로 분류

#### [NEW] [OpenApiGenerator.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/OpenApiGenerator.java)
- `StaticScanResult`와 `directConditions` 및 정규화된 `ApiCondition` 목록을 조합하여 최종 OpenAPI 3.0 사양서 빌드:
  - YAML 포맷 출력 빌더
  - 각 스키마 필드별로 validation 속성(`required`, `minLength`, `pattern` 등)을 변환하여 매핑 주입

#### [NEW] [OpenApiAssemblyService.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/OpenApiAssemblyService.java)
- 전체 프로세스 조정 서비스:
  - `assemble(StaticScanResult scanResult, ValidationExtractionResult extractResult, RepositorySource source, Path outputPath) throws IngestionException;`
  - 1. 추출 결과를 `LlmNormalizationService`를 통해 정규화
  - 2. `OpenApiGenerator`를 통해 OpenAPI YAML 문자열을 빌드
  - 3. 대상 출력 경로(`outputPath`)에 YAML 파일 생성 및 저장
  - 4. 부분 실패 발생 시 엔드포인트별 분리하여 warnings 처리

---

### [Component: Test Suite]

#### [NEW] [OpenApiAssemblyServiceTest.java](file:///d:/workspace/auto-oas/src/test/java/io/atworks/specscan/analysis/OpenApiAssemblyServiceTest.java)
- **정규화 및 검증 테스트**: LLM 응답 스키마 정합성과 retry/rejected 상태 분기를 검증
- **OpenAPI 3.0 YAML 빌드 테스트**: 생성된 YAML 텍스트 내에 엔드포인트 경로, DTO Schema 정의 및 validation 제약사항(required, size 등)이 정상 표현되었는지 검증
- **부분 실패 저장 테스트**: 특정 엔드포인트 파싱 에러 발생 시 정상적인 엔드포인트만 취합하여 OpenAPI 문서를 정상 빌드하고 실패 내역이 분리 기록되는지 검증

---

## Verification Plan

### Automated Tests
```powershell
./gradlew test
```

### Manual Verification
- 빌드된 openapi.yaml 파일을 열어, YAML 문법 및 OpenAPI 3.0 Spec 스키마 사양이 올바른지 수동 확인합니다.
