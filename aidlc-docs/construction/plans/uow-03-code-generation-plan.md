# UOW-03 Code Generation Plan

`UOW-03: Validation Candidate Extraction` 유닛에 대한 코드 생성 계획입니다. 본 계획에 따라 엔드포인트에 바인딩된 DTO 필드의 표준 Bean Validation 어노테이션으로부터 직접적인 검증 조건(`ApiConditionDraft`)을 추출하고, 커스텀 어노테이션, 스프링/커스텀 Validator, 서비스 레이어 내부의 비즈니스 예외 던지기(`if-throw BusinessException`) 흐름을 정적 분석하여 다단계 유효성 검증 후보군(`ValidationCandidate`)을 추출하는 엔진을 구현합니다.

## User Review Required

> [!IMPORTANT]
> **핵심 아키텍처 및 구현 결정 사항**
> 1. **다단계 추출 레이어 구성**:
>    - **어노테이션 레이어**: DTO의 표준 어노테이션(`@NotNull`, `@Size` 등)은 direct `ApiConditionDraft`로 분류하고, 커스텀/미지원 어노테이션은 `ValidationCandidate`로 처리해 evidence와 trace를 보존합니다.
>    - **Validator 레이어**: `ConstraintValidator` 연동 구조, Spring `Validator` 구현체 및 `@InitBinder` 바인딩 메서드를 추적해 `errors.rejectValue(...)` 등의 호출부로부터 필드별 검증 후보를 도출합니다.
>    - **서비스/도메인 레이어**: 컨트롤러 메서드 내에서 호출하는 서비스의 메서드를 추적하여, 해당 메서드 내부의 `if` 조건문 및 `throw new BusinessException(...)` 패턴을 정적 파싱하고 간접 검증 힌트와 confidence(간접 매핑 시 0.5)를 계산합니다.
> 2. **결함 격리 및 Fallback**: 복잡한 서비스 체인이나 파싱하기 어려운 메서드 바디를 만났을 때, 전체 프로세스를 실패시키는 대신 분석이 가능한 raw snippet을 fallback 텍스트 정보로 보존하고 warnings 목록에 기록합니다.

## Proposed Changes

### [Component: Domain Models]
추출된 검증 규격과 후보 정보를 표현하는 모델을 정의합니다.

#### [NEW] [ApiConditionDraft.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/ApiConditionDraft.java)
- 표준 어노테이션 매핑을 통해 직접 승격된 검증 조건 모델:
  - `targetPath` (DTO 필드 경로)
  - `operator` (NOT_NULL, SIZE, PATTERN, MIN, MAX 등)
  - `expected` (상숫값 또는 표현식 문자열)
  - `evidence` (코드 원본 텍스트)
  - `sourceTrace` (`SourceTrace`)

#### [NEW] [ValidationCandidate.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/ValidationCandidate.java)
- LLM에 전달하여 정규화해야 하는 원본 검증 후보 모델:
  - `candidateId` (고유 식별자)
  - `sourceType` (CUSTOM_ANNOTATION, VALIDATOR, SERVICE_HINT 등)
  - `targetPath` (필드 경로 또는 식별 가능한 식별자)
  - `evidenceSnippet` (검증 규칙이 포함된 소스 코드 스니펫)
  - `confidence` (추론 신뢰도, 직접=1.0, 간접/추론=0.5)
  - `sourceTrace` (`SourceTrace`)

#### [NEW] [ValidationExtractionResult.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/ValidationExtractionResult.java)
- 유효성 추출 프로세스의 최종 반환 Aggregate:
  - `directConditions` (`List<ApiConditionDraft>`)
  - `candidates` (`List<ValidationCandidate>`)
  - `warnings` (`List<IngestionWarning>`)

---

### [Component: Extractors]
각 레이어별로 JavaParser AST를 순회하여 유효성 정보를 발굴하는 컴포넌트입니다.

#### [NEW] [AnnotationConditionExtractor.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/AnnotationConditionExtractor.java)
- DTO 필드에 부착된 애노테이션을 파싱:
  - 표준 JSR-380/Hibernate Validator 어노테이션 -> `ApiConditionDraft` 생성
  - 그 외 커스텀/미지원 어노테이션 -> `ValidationCandidate`로 변환하여 snippet과 trace 보존

#### [NEW] [ValidatorCandidateExtractor.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/ValidatorCandidateExtractor.java)
- `ConstraintValidator` 구현체 및 컨트롤러 내 `@InitBinder` 및 Spring `Validator` 구현체를 탐색:
  - `errors.rejectValue(...)` 나 `errors.reject(...)` 호출 노드의 아규먼트(필드명, 에러코드)를 정적 추출하여 `ValidationCandidate` 생성
  - 커스텀 어노테이션과 연동되는 `ConstraintValidator`인 경우, 어노테이션 정의부와 검증 로직 소스를 하나의 trace로 결합

#### [NEW] [ServiceHintExtractor.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/ServiceHintExtractor.java)
- 서비스 클래스 분석:
  - 컨트롤러에서 호출하는 서비스의 메서드를 파싱
  - 메서드 내부의 `if (...) throw new BusinessException(...)` 구조를 찾아 `ValidationCandidate`로 추출
  - 간접 매핑(컨트롤러의 파라미터 DTO 필드와 직접 연결이 안 되고 내부 변수 검증을 거치는 형태)일 때, 매핑 추론 힌트를 trace 및 0.5 confidence와 함께 기록

---

### [Component: Application Services]

#### [NEW] [ValidationExtractionService.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/ValidationExtractionService.java)
- `ValidationExtractionResult extract(StaticScanResult staticScanResult, RepositorySource repositorySource) throws IngestionException;`
- **핵심 로직**:
  1. `StaticScanResult`에 등록된 엔드포인트 목록을 조회
  2. 각 엔드포인트에 연동된 Request DTO 및 컨트롤러 클래스에 대해 `AnnotationConditionExtractor` 및 `ValidatorCandidateExtractor` 가동
  3. 컨트롤러 메서드가 내부적으로 참조/호출하는 서비스 클래스 명세가 있는 경우 `ServiceHintExtractor` 가동
  4. 도출된 모든 `ApiConditionDraft`와 `ValidationCandidate`를 취합하여 `ValidationExtractionResult` 빌드 및 반환

---

### [Component: Test Suite]

#### [NEW] [ValidationExtractionServiceTest.java](file:///d:/workspace/auto-oas/src/test/java/io/atworks/specscan/analysis/ValidationExtractionServiceTest.java)
- **표준 어노테이션 테스트**: `@NotNull`, `@Size(min=5)` 등이 선언된 DTO를 분석하여 올바른 `ApiConditionDraft`가 생성되는지 검증
- **커스텀 Validator 테스트**: `Validator` 구현체 내 `rejectValue("username", "invalid.username")`를 파싱하여 `ValidationCandidate`로 도출되는지 검증
- **서비스 예외 힌트 테스트**: `if (user.getAge() < 19) throw new IllegalArgumentException("Underage")`가 서비스 레이어에 있을 때, 스니펫 및 trace를 포함한 `ValidationCandidate`(confidence 0.5)가 안전하게 추출되는지 검증

---

## Verification Plan

### Automated Tests
구현 완료 후 다음 테스트 명령을 통해 전체 테스트 스위트를 검증합니다.
```powershell
./gradlew test
```

### Manual Verification
- 컨트롤러 내에서 어떠한 검증 어노테이션이나 예외 처리가 존재하지 않을 때, 에러 없이 비어있는 결과가 정상적으로 리턴되는지 검증합니다.
