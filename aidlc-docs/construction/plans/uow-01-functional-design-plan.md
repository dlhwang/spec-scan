# UOW-01 Functional Design Plan

## Unit Context
- Unit: `UOW-01`
- Title: Repository Ingestion
- Related story: `S-01`
- Goal: GitHub repository URL을 받아 정적 분석 가능한 임시 workspace와 source metadata를 준비한다.

## Execution Checklist
- [ ] `UOW-01`의 business workflow를 정의한다.
- [ ] repository ingestion domain model을 정의한다.
- [ ] ingestion business rules와 safety rules를 정의한다.
- [ ] ingestion input/output data flow를 정의한다.
- [ ] failure scenarios와 error handling rules를 정의한다.
- [ ] `business-logic-model.md`를 생성한다.
- [ ] `business-rules.md`를 생성한다.
- [ ] `domain-entities.md`를 생성한다.

## Functional Design Focus
- repository URL validation
- clone/fetch workflow
- temporary workspace lifecycle
- source root detection
- static-analysis-only safety enforcement
- structured failure reporting

## Planning Questions

## Question 1
repository ingestion의 입력 계약은 어느 수준까지 엄격하게 검증할까요?

A) GitHub URL 문자열이면 우선 허용

- clone 단계에서 실패하면 그때 오류를 반환한다.

B) URL 형식과 GitHub repository 형태를 먼저 검증

- `owner/repo`, `.git`, branch/ref query 등 허용 규칙을 명확히 둔다.

C) B안 + 분석 금지 대상 규칙까지 선검증

- 예: 로컬 경로, 비-GitHub URL, archive URL, release asset URL 등 차단

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + 분석 금지 대상 규칙까지 선검증

GitHub Repository URL 기반 PoC이므로 입력은 GitHub repository URL로 제한한다. 단순 문자열을 clone 단계까지 넘기면 실패 원인이 늦게 드러나고, 이후 단계에서 처리해야 할 예외가 많아진다.

허용:

* `https://github.com/{owner}/{repo}`
* `https://github.com/{owner}/{repo}.git`
* optional branch/ref/commit 입력은 별도 필드로 허용

차단:

* 로컬 파일 경로
* 비-GitHub URL
* archive URL
* release asset URL
* raw file URL
* SSH URL
* URL이 아닌 임의 문자열

PoC에서는 범위를 좁혀 GitHub repository URL만 명확하게 지원한다.

## Question 2
임시 workspace lifecycle은 어떻게 다룰까요?

A) 매 실행마다 새 directory 생성 후 결과만 남긴다

- 가장 단순하지만 재실행 비용이 있다.

B) repository URL 기준 캐시/재사용 전략을 둔다

- 같은 repository는 재사용 가능하게 한다.

C) hybrid

- 기본은 새 workspace 생성, 다만 명시적 캐시 포인트만 남긴다.

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) hybrid

기본은 매 실행마다 새 임시 workspace를 생성한다. 이렇게 해야 실행 단위가 격리되고, 이전 분석 결과나 파일 상태가 다음 실행에 섞이지 않는다.

다만 향후 재실행 비용을 줄이기 위해 cache key와 cache directory 설계 포인트는 남긴다. PoC 1차 구현에서는 cache reuse를 기본 동작으로 만들지 않고, 명시적 확장 포인트로만 둔다.

즉, 기본 동작은 isolated temporary workspace이고, cache는 optional extension point로 설계한다.

## Question 3
source root detection은 어느 수준까지 지원할까요?

A) 일반적인 단일 Maven/Gradle Spring 프로젝트만 우선 지원

B) 멀티모듈까지 지원하되, 첫 PoC에서는 대표 source root만 선택

C) 멀티모듈과 복수 source root inventory를 모두 유지

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) 멀티모듈과 복수 source root inventory를 모두 유지

Spring 프로젝트는 단일 모듈일 수도 있고 Gradle/Maven 멀티모듈일 수도 있다. PoC라도 분석 대상 source root를 하나로 강제로 줄이면 이후 endpoint 누락 가능성이 커진다.

따라서 ingestion 단계에서는 복수 source root inventory를 유지한다.

단, 다음 분석 단계에서 우선순위를 둘 수 있도록 metadata를 함께 제공한다.

예:

* root path
* module name
* build tool hint
* `src/main/java` 존재 여부
* Java file count
* Spring annotation candidate count
* selection priority

## Question 4
ingestion 단계의 business output은 어디까지 포함할까요?

A) clone 경로와 source root만 반환

B) clone 경로, source roots, build tool 힌트, Java 파일 inventory 요약까지 반환

C) B안 + 분석 제외 사유와 경고 목록까지 반환

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + 분석 제외 사유와 경고 목록까지 반환

ingestion 결과는 단순 clone path만으로는 부족하다. 이후 정적 분석과 실패 추적을 위해 source root, build tool hint, Java file inventory, warning 정보가 필요하다.

반환 대상:

* repository URL
* normalized repository identity
* workspace path
* detected source roots
* build tool hint
* Java file inventory summary
* excluded paths
* warnings
* hard failure 여부
* source ingestion metadata

분석 제외 사유와 경고 목록을 포함해야 이후 workflow에서 “왜 특정 모듈이 분석되지 않았는지” 추적할 수 있다.

## Question 5
오류 처리 모델은 어떻게 정의할까요?

A) 첫 실패에서 전체 ingestion 실패

B) hard failure와 soft warning을 분리

- 예: clone 실패는 hard failure, 일부 비표준 디렉터리 감지는 warning

C) B안 + machine-readable error code 체계 정의

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + machine-readable error code 체계 정의

clone 실패, URL 검증 실패, workspace 생성 실패 같은 오류는 hard failure로 처리한다. 반면 일부 비표준 디렉터리, source root 후보 없음, 일부 파일 접근 실패 같은 상황은 soft warning으로 분리한다.

오류는 사람이 읽는 message뿐 아니라 machine-readable error code를 가진다.

예:

* `INVALID_REPOSITORY_URL`
* `UNSUPPORTED_REPOSITORY_SOURCE`
* `CLONE_FAILED`
* `WORKSPACE_CREATE_FAILED`
* `SOURCE_ROOT_NOT_FOUND`
* `JAVA_FILE_SCAN_FAILED`
* `STATIC_ANALYSIS_POLICY_VIOLATION`

이렇게 해야 이후 application workflow와 테스트에서 실패 원인을 안정적으로 검증할 수 있다.

## Question 6
정적 분석 전용 safety rule을 ingestion에서 어디까지 강제할까요?

A) "실행하지 않는다" 원칙만 문서화

B) build/test/run 명령 호출 금지와 파일 수집 범위 제한을 명시한다

C) B안 + 허용된 파일 접근 종류와 금지된 후속 동작 힌트까지 business rule로 정의한다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + 허용된 파일 접근 종류와 금지된 후속 동작 힌트까지 business rule로 정의한다

PoC에서 Security extension rules는 blocking constraint로 강제하지 않더라도, ingestion 단계의 기본 safety rule은 필요하다. 외부 GitHub repository를 다루기 때문에 build, test, run 같은 임의 코드 실행은 금지한다.

허용:

* repository clone
* 디렉터리 탐색
* Java/source/config 파일 읽기
* build file metadata 읽기
* source root inventory 생성

금지:

* `gradle build`, `mvn test`, `npm install` 등 명령 실행
* 애플리케이션 run
* 테스트 실행
* repository 내부 script 실행
* 임의 binary 실행
* clone된 코드의 side-effect 발생 동작

ingestion 산출물에는 후속 단계도 정적 분석 전용으로 동작해야 한다는 safety hint를 포함한다.

## Approval Notes
- 모든 `[Answer]:` 항목이 채워진 뒤에만 `UOW-01` functional design 산출물 생성을 진행한다.
- 모호한 답변이 있으면 follow-up question을 같은 문서에 추가한다.
- 답변 완료 후 `aidlc-docs/construction/uow-01/functional-design/` 아래 산출물을 생성한다.

## Answer Analysis
- 입력 계약은 GitHub repository URL만 허용하는 강한 선검증 방식으로 확정되었다.
- workspace lifecycle은 기본 격리 실행 + 향후 캐시 확장 포인트 유지 방식으로 정리되었다.
- source root detection은 멀티모듈과 복수 source root inventory 유지가 필요하다는 점이 확정되었다.
- ingestion output은 단순 경로 반환이 아니라 source root inventory, exclusion reason, warnings를 포함하는 풍부한 metadata를 반환해야 한다.
- 오류는 hard failure와 soft warning을 분리하고 machine-readable error code를 가져야 한다.
- safety rule은 금지 명령과 허용된 파일 접근 범위를 명시하는 강한 정적 분석 전용 정책으로 정의한다.
- 답변 간 충돌이나 모호성은 없어 follow-up question은 필요하지 않다.
