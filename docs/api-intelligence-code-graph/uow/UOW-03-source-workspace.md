# UOW-03 Source Workspace 취득

## Goal

로컬 경로와 HTTP(S) Git 저장소를 공통 `SourceWorkspacePort`로 제공하고 사용자 로컬 파일을 변경하거나 삭제하지 않는다.

## 선행 조건

UOW-01 완료.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/port/out/SourceWorkspacePort.java`
- `src/main/java/io/atworks/apiintelligence/domain/source/SourceWorkspace.java`
- `src/main/java/io/atworks/apiintelligence/adapter/source/**`
- `src/test/java/io/atworks/apiintelligence/adapter/source/**`

## 필수 계약

- Git은 HTTP/HTTPS만 허용하고 redirect 후 URI도 재검증한다.
- branch, tag, commit 및 revision 생략 시 remote default HEAD를 지원한다.
- resolved commit ID와 Java source roots를 반환한다.
- build tool이나 대상 코드를 실행하지 않는다.
- 로컬 경로는 존재, directory, readable을 검증한다.
- 로컬 cleanup은 no-op이며 clone 임시 폴더만 실패 경로까지 정리한다.
- 기존 ingestion application/domain 모델을 사용하지 않는다.
- 오류: `LOCAL_PATH_NOT_FOUND`, `LOCAL_PATH_NOT_READABLE`, `GIT_CLONE_FAILED`,
  `GIT_REVISION_NOT_FOUND`

## 구현 절차

1. root, source roots, resolved revision, ownership을 가진 workspace handle을 정의한다.
2. read-only local adapter와 no-op cleanup을 구현한다.
3. JGit mechanics만 사용한 Git adapter를 구현한다.
4. 일반 Maven/Gradle multi-module source root를 탐색한다.
5. clone workspace의 소유권과 cleanup을 검증한다.

## 테스트와 완료 조건

- local success/missing/not-directory/unreadable
- local cleanup이 원본을 삭제하지 않음
- local Git fixture의 default HEAD/branch/tag/commit
- invalid protocol/revision과 실패 cleanup
- 두 source가 동일 port를 만족하고 기존 ingestion 모델 의존 없음

