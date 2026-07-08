# UOW-01 Code Generation Plan

`UOW-01: Repository Ingestion` 유닛에 대한 코드 생성 계획입니다. 본 계획에 따라 외부 GitHub Repository를 안전하게 임시 워크스페이스에 복제(Fetch)하고, 프로젝트의 빌드 도구(Maven/Gradle) 레이아웃 및 Java 소스 디렉터리를 정적 분석하여 `RepositorySource` 메타데이터를 구축하는 컴포넌트를 구현합니다.

## User Review Required

> [!IMPORTANT]
> **핵심 아키텍처 및 구현 결정 사항**
> 1. **빌드 도구 선택**: Greenfield PoC 개발을 위해 Gradle(`build.gradle`)을 빌드 환경으로 선택합니다.
> 2. **Java 버전**: 최신 LTS 버전인 **Java 21**을 사용합니다.
> 3. **외부 라이브러리 의존성**: Git 조작을 위해 `org.eclipse.jgit`을 사용하며, JSON 직렬화 및 메타데이터 표현을 위해 `jackson-databind`를 의존성에 추가합니다.
> 4. **안전성(Safety) 가드레일**: `BR-014`에 정의된 정적 분석 전용 안전 원칙에 따라 임의의 빌드/테스트 명령어 실행이나 스크립트 실행은 철저히 배제하고, 순수 파일 읽기 및 디렉터리 순회 로직으로만 구현합니다.

## Proposed Changes

### [NEW] [build.gradle](file:///d:/workspace/auto-oas/build.gradle)
Gradle 프로젝트 빌드 구성 및 의존성을 정의합니다.
- Java 버전: 21
- 플러그인: `java`
- 의존성:
  - implementation: `org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r`
  - implementation: `com.fasterxml.jackson.core:jackson-databind:2.15.2`
  - testImplementation: `org.junit.jupiter:junit-jupiter:5.9.3`
  - testImplementation: `org.assertj:assertj-core:3.24.2`

---

### [Component: Domain Models]
`domain-entities.md`의 설계에 따라 핵심 데이터 모델 및 예외/에러 코드를 정의합니다.

#### [NEW] [IngestionErrorCode.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/IngestionErrorCode.java)
- `INVALID_REPOSITORY_URL`, `UNSUPPORTED_REPOSITORY_SOURCE`, `WORKSPACE_CREATE_FAILED`, `CLONE_FAILED`, `STATIC_ANALYSIS_POLICY_VIOLATION` 등 에러 코드 정의.

#### [NEW] [IngestionException.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/IngestionException.java)
- Ingestion 중단 시 발생하는 커스텀 예외 클래스. `IngestionErrorCode`를 포함합니다.

#### [NEW] [RepositoryRequest.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/RepositoryRequest.java)
- `repositoryUrl`, `branch`, `tag`, `commit`을 담는 Record.

#### [NEW] [RepositoryIdentity.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/RepositoryIdentity.java)
- 파싱 및 검증된 `host`, `owner`, `repositoryName`, `normalizedCloneUrl`, `requestedRef`를 담는 Record.

#### [NEW] [WorkspaceContext.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/WorkspaceContext.java)
- 임시 워크스페이스 경로 및 실행 식별자(`executionId`), 생성시각을 표현하는 Record.

#### [NEW] [SourceRootCandidate.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/SourceRootCandidate.java)
- 탐색된 Java 소스 루트 후보 정보 (`rootPath`, `moduleName`, `buildToolHint`, `hasMainJava`, `javaFileCount`, `springAnnotationCandidateCount`, `selectionPriority`).

#### [NEW] [JavaInventorySummary.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/JavaInventorySummary.java)
- `totalJavaFileCount`, `sourceRootCount`, `moduleCount` 등의 통계 정보.

#### [NEW] [ExcludedPathRecord.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/ExcludedPathRecord.java)
- 제외된 디렉터리 경로 및 사유 코드/메시지.

#### [NEW] [IngestionWarning.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/IngestionWarning.java)
- soft warning 정보 (코드, 메시지, 관련 경로).

#### [NEW] [SafetyPolicyHint.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/SafetyPolicyHint.java)
- downstream 단계를 위한 allowed/forbidden actions 명시.

#### [NEW] [IngestionMetadata.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/IngestionMetadata.java)
- 시작/완료 시각 및 처리 시간, warning 개수 관리.

#### [NEW] [RepositorySource.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/domain/RepositorySource.java)
- 위의 모든 도메인 모델을 소유하는 Aggregate Root.

---

### [Component: Ports and Adapters]
Git Fetch 및 Workspace 생성 메커니즘을 캡슐화합니다.

#### [NEW] [RepositoryFetcherPort.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/port/RepositoryFetcherPort.java)
- `void fetch(RepositoryIdentity identity, Path targetPath) throws IngestionException;`

#### [NEW] [WorkspacePreparerPort.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/port/WorkspacePreparerPort.java)
- `WorkspaceContext prepare() throws IngestionException;`
- `void clean(WorkspaceContext context);`

#### [NEW] [GitRepositoryFetcherAdapter.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/adapter/GitRepositoryFetcherAdapter.java)
- `JGit` 라이브러리를 활용해 target path로 리포지토리를 복제합니다.
- 특정 Ref(Branch, Tag, Commit)가 주어지면 checkout 또는 fetch 후 reset을 수행합니다.

#### [NEW] [TempWorkspacePreparerAdapter.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/adapter/TempWorkspacePreparerAdapter.java)
- `java.nio.file.Files.createTempDirectory`를 통해 시스템 임시 폴더 내에 고유한 workspace를 할당합니다.
- 종료 시 workspace 하위 리소스를 안전하게 삭제하는 clean 메서드를 구현합니다.

---

### [Component: Application Services]
비즈니스 워크플로를 오케스트레이션하고 소스 분석 로직을 구현합니다.

#### [NEW] [RepositoryIngestionService.java](file:///d:/workspace/auto-oas/src/main/java/io/atworks/specscan/ingestion/application/RepositoryIngestionService.java)
- `RepositorySource ingest(RepositoryRequest request) throws IngestionException;`
- **핵심 로직**:
  1. 입력 URL 검증 및 파싱 (BR-001, BR-002 규칙 적용)
  2. 임시 워크스페이스 준비 (WorkspacePreparerPort 호출)
  3. Git Clone/Fetch 실행 (RepositoryFetcherPort 호출)
  4. 소스 디렉터리 순회 및 인벤토리 조사:
     - `pom.xml`, `build.gradle` 검출을 통해 빌드 도구 힌트 판단
     - 멀티모듈 프로젝트인 경우 각 서브 모듈의 `src/main/java` 경로 식별
     - Java 파일 개수 및 `@RestController`, `@Controller`, `@RequestMapping` 등 Spring annotation 문자열 정적 카운팅
     - `.git`, `build`, `target`, `out`, `node_modules` 등 정적 분석과 무관한 폴더는 ExcludedPathRecord로 등록
  5. RepositorySource 최종 어셈블리 및 반환

---

### [Component: Test Suite]
구현의 정확성과 안정성을 보장하기 위해 단위 테스트를 생성합니다.

#### [NEW] [RepositoryIngestionServiceTest.java](file:///d:/workspace/auto-oas/src/test/java/io/atworks/specscan/ingestion/RepositoryIngestionServiceTest.java)
- Mock 포트를 사용하여 Ingestion Service의 워크플로 제어(예외 전파, 검증 분기) 및 파일 순회 로직(Maven/Gradle 단일/멀티모듈 가짜 디렉터리 구성 후 검사)을 독립적으로 검증합니다.

#### [NEW] [GitRepositoryFetcherAdapterTest.java](file:///d:/workspace/auto-oas/src/test/java/io/atworks/specscan/ingestion/GitRepositoryFetcherAdapterTest.java)
- 실제 소규모 공용 GitHub Repository 또는 Mock Git Repository를 대상으로 fetch 및 checkout 기능이 정상적으로 도는지 테스트합니다.

---

## Verification Plan

### Automated Tests
구현 완료 후 다음 테스트 명령을 통해 전체 테스트 스위트를 검증합니다.
```powershell
./gradlew test
```

### Manual Verification
- 올바르지 않은 GitHub URL 문자열을 입력으로 주었을 때 `UNSUPPORTED_REPOSITORY_SOURCE` 에러 코드가 담긴 예외가 제대로 반환되는지 확인합니다.
- 실제 임시 디렉터리가 생성되고, 예외 발생 또는 정상 종료 후에 클리너가 작동하여 임시 디렉터리가 소멸되는지 검증합니다.
