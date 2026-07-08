# UOW-02 Code Generation Plan

`UOW-02: Endpoint and Type Analysis` 유닛에 대한 코드 생성 계획입니다. 본 계획에 따라 Spring MVC 컨트롤러 소스 코드를 정적으로 분석하여 HTTP 엔드포인트 목록, 요청 파라미터 매핑 위치(HEADER/PATH/QUERY/BODY), 응답 타입을 추출하며, 소스 위치 추적(Source Trace) 및 DTO 타입 필드 해석을 수행하는 엔진을 구현합니다.

## User Review Required

> [!IMPORTANT]
> **핵심 아키텍처 및 구현 결정 사항**
> 1. **AST 파싱 라이브러리 도입**: 정밀한 Java 소스 분석 및 컴포넌트/DTO 필드 구조 분석을 위해 **JavaParser (`com.github.javaparser:javaparser-core:3.25.7`)** 라이브러리를 `build.gradle`에 추가합니다.
> 2. **정적 분석 스캔 범위**: `@RestController` 및 `@Controller` 애노테이션이 선언된 클래스를 탐색하여 엔드포인트 정보를 도출합니다.
> 3. **파라미터 바인딩(Request Target Location) 매핑 규칙**:
>    - `@RequestHeader` -> `HEADER`
>    - `@PathVariable` -> `PATH`
>    - `@RequestParam` -> `QUERY`
>    - `@RequestBody` -> `BODY`
>    - 애노테이션이 없는 단순 기본 타입(Primitive, String) -> `QUERY`
>    - 애노테이션이 없는 사용자 정의 DTO(POJO) -> `QUERY` (Spring MVC의 `@ModelAttribute` 바인딩 동작 방식 반영)
> 4. **제네릭 응답 타입 해제**: 컨트롤러가 반환하는 `ResponseEntity<T>` 또는 `HttpEntity<T>` 등 래퍼 타입의 제네릭 아규먼트 `T`를 분석하여 실제 응답 Body DTO 타입을 온전히 추출합니다.

## Proposed Changes

### [MODIFY] [build.gradle](file:///d:/workspace/auto-oas/build.gradle)
- AST 파싱을 위한 `javaparser-core` 의존성을 추가합니다.
```groovy
dependencies {
    implementation 'com.github.javaparser:javaparser-core:3.25.7'
    ...
}
```

---

### [Component: Domain Models]
엔드포인트 스캔 분석 결과를 표현하기 위한 도메인 레코드 및 모델을 정의합니다.

#### [NEW] [SourceTrace.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/SourceTrace.java)
- 분석된 대상의 파일 상대 경로, 시작 라인 번호, 종료 라인 번호를 보존하는 Record (이후 단계에서도 공통 활용).

#### [NEW] [BindingLocation.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/BindingLocation.java)
- HTTP 요청 파라미터가 바인딩되는 위치를 나타내는 Enum: `HEADER`, `PATH`, `QUERY`, `BODY`.

#### [NEW] [RequestBinding.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/RequestBinding.java)
- HTTP 요청 데이터 매핑 명세를 담는 Record:
  - `parameterName` (파라미터명)
  - `targetLocation` (`BindingLocation` enum)
  - `type` (데이터 타입)
  - `isRequired` (필수 여부)
  - `sourceTrace` (`SourceTrace`)

#### [NEW] [ResponseBinding.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/ResponseBinding.java)
- HTTP 응답 데이터 명세를 담는 Record:
  - `type` (반환 타입 명세)
  - `sourceTrace` (`SourceTrace`)

#### [NEW] [ApiEndpoint.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/ApiEndpoint.java)
- 스캔 완료된 개별 API 엔드포인트 핵심 정보를 담는 Record:
  - `httpMethod` (GET, POST, PUT, DELETE, PATCH 등)
  - `path` (최종 계산된 API 라우팅 경로)
  - `controllerClass` (컨트롤러 클래스 풀네임)
  - `controllerMethod` (컨트롤러 메서드명)
  - `requestBindings` (`List<RequestBinding>`)
  - `responseBinding` (`ResponseBinding`)
  - `sourceTrace` (`SourceTrace`)

#### [NEW] [StaticScanResult.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/domain/StaticScanResult.java)
- 분석 결과를 취합한 최종 Aggregate Root:
  - `endpoints` (`List<ApiEndpoint>`)
  - `scannedClassesCount` (스캔된 총 클래스 수)
  - `warnings` (`List<IngestionWarning>`)
  - `metadata` (`IngestionMetadata`)

---

### [Component: Support & Utilities]
JavaParser AST를 활용하여 구체적인 정적 스캔을 대행하는 컴포넌트입니다.

#### [NEW] [SourceTraceResolver.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/SourceTraceResolver.java)
- JavaParser Node 객체를 인자로 받아, 해당 노드가 정의된 파일 상대 경로 및 라인 정보를 계산해 `SourceTrace` 객체로 생성해주는 헬퍼.

#### [NEW] [TypeResolver.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/TypeResolver.java)
- DTO 타입 정보 및 컨트롤러 파라미터의 실제 타입 세부 명세를 분석하기 위해, 리포지토리 소스 디렉터리 내에서 클래스 선언 파일을 찾아 해당 클래스 내부의 멤버 필드(이름, 타입, 애노테이션) 정보를 정적으로 재귀 분석하는 해석기.

#### [NEW] [EndpointExtractor.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/support/EndpointExtractor.java)
- 개별 Controller 클래스 파일을 JavaParser AST로 파싱하여:
  - `@RestController`, `@Controller` 여부 확인
  - 클래스 및 메서드 레벨의 Mapping 애노테이션 결합하여 최종 API Endpoint URL 계산
  - 메서드 아규먼트들을 분석하여 Request Binding 매핑 정보 추출
  - 메서드 리턴 타입을 파싱하여 Response Binding 정보 추출

---

### [Component: Application Services]
비즈니스 스캔 워크플로를 오케스트레이션합니다.

#### [NEW] [SpringStaticScanService.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/analysis/application/SpringStaticScanService.java)
- `StaticScanResult scan(RepositorySource repositorySource) throws IngestionException;`
- **핵심 로직**:
  1. `RepositorySource`에 등록된 활성 소스 디렉터리(`src/main/java`) 목록 확인
  2. 디렉터리 내의 모든 `.java` 소스 파일을 탐색하여 컨트롤러 후보 추출
  3. 각 컨트롤러 소스 파일에 대해 `EndpointExtractor`를 호출하여 `ApiEndpoint` 리스트 수집
  4. 도중 파싱 에러 발생 시 로그 및 warning에 기록하며 스캔 유지 (결함 격리 원칙)
  5. 엔드포인트 목록과 경고 목록, 통계를 취합하여 `StaticScanResult` 반환

---

### [Component: Test Suite]
구현 적합성을 정밀하게 검증할 테스트 코드입니다.

#### [NEW] [SpringStaticScanServiceTest.java](file:///d:/workspace/auto-oas/src/test/java/io/atworks/specscan/analysis/SpringStaticScanServiceTest.java)
- 가상의 Spring 컨트롤러 파일(다양한 HTTP Method 매핑, DTO Body, PathVariable, RequestParam, Header 바인딩을 가지고 ResponseEntity 반환 구조를 취함)을 임시 테스트 폴더에 생성하고, `SpringStaticScanService`가 각 속성과 바인딩 위치를 정확하게 매핑해 내는지 통합 테스트합니다.
- 복잡 제네릭 반환 타입 및 애노테이션이 없는 파라미터 기본 분류 동작을 정밀 검사합니다.

---

## Verification Plan

### Automated Tests
구현 완료 후 다음 테스트 명령을 통해 컴파일 에러가 없고 모든 단위/통합 테스트가 통과하는지 검증합니다.
```powershell
./gradlew test
```

### Manual Verification
- 파싱할 수 없는 비정상 Java 문법 파일이 리포지토리에 포함되어 있을 때, 스캔 프로세스가 중단되지 않고 warning으로 기록되며 다른 정상 파일 분석은 성공적으로 이루어지는지 검증합니다.
