# API Documentation

## REST APIs
- 이 아티팩트 자체는 네트워크 REST API를 제공하지 않는다.
- 관찰 가능한 외부 인터페이스는 CLI 입력 인자와 파일 출력이다.

## CLI Interface
### Main Entry
- **Command**: `java -jar auto-oas.jar <projectPath> [restApiModulePath] [enableExceptionLogging] <outputPath>`
- **Purpose**: Java REST API 소스를 분석하고 OpenAPI 명세 파일을 생성한다.

### Argument Modes
- **2 arguments**: `<projectPath> <outputPath>`
- **3 arguments**: `<projectPath> <restApiModulePath> <outputPath>`
- **4 arguments**: `<projectPath> <restApiModulePath> <enableExceptionLogging> <outputPath>`

## Internal APIs
### `at.aau.serg.parsers.ParserFactory`
- **Methods**:
  - `registerRestFramework(RestFramework)`
  - `createParser(String, String, String)`
  - `createParserWithDetection(String, String, String, boolean)`
  - `createParserWithDetection(String, String, boolean)`
- **Parameters**: 프로젝트 경로, REST 모듈 경로, 출력 경로, 예외 로깅 플래그
- **Return Types**: `RestApiParser`

### `at.aau.serg.parsers.FrameworkDetector`
- **Methods**:
  - `detectFramework(String)`
  - `getModel()`
- **Parameters**: 분석 대상 프로젝트 루트
- **Return Types**: `RestFramework`, `CtModel`

### `at.aau.serg.parsers.RestApiParser`
- **Methods**:
  - `run()`
  - `getRelevantClassesFromPackages(Collection<CtPackage>)`
- **Parameters**: 내부적으로 프로젝트 이름, 모듈 경로, 선택된 프레임워크, Spoon 모델
- **Return Types**: 실행 부수효과 중심, `RelevantClasses`

### `at.aau.serg.openapi.OpenApiGenerator`
- **Methods**:
  - `getDummyInfo(String)`
  - `getDummyInfo(String, String)`
  - `createOpenApi(Info, Paths, Components)`
  - `writeOpenApiToFile(OpenAPI, String)`
- **Parameters**: OpenAPI 메타데이터, 경로 모델, 스키마 모델, 출력 파일 경로
- **Return Types**: `Info`, `OpenAPI`, 파일 출력

## Data Models
### OpenAPI
- **Fields**: `info`, `paths`, `components`
- **Relationships**: `Paths`와 `Components`를 포함
- **Validation**: Jackson 직렬화 시 `null` 필드는 제외

### RelevantClasses
- **Fields**: 역참조된 모델 타입과 컨트롤러 후보군
- **Relationships**: `RestApiParser`의 컨트롤러/모델 추출에 사용
- **Validation**: 패키지와 모듈 경계에 따라 필터링

### ControllerClassProcessingInformation
- **Fields**: 컨트롤러 타입, 경로, 하위 리소스 정보
- **Relationships**: `createPathsFromControllers`와 sub-resource 탐색에 사용
- **Validation**: 프레임워크 규칙에 따라 파생

## Output Contract
- **Format**: Pretty-printed JSON
- **Directory Behavior**: 상위 디렉터리가 없으면 자동 생성
- **Overwrite Behavior**: 기존 파일이 있으면 삭제 후 새로 작성
- **Error Behavior**: 파일 출력 예외는 `stderr`에 기록
