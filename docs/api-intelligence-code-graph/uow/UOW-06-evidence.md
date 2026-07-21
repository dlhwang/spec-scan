# UOW-06 Evidence Catalog 생성

## Goal

API별 graph source location에서 정확한 라인 범위의 최소 snippet과 결정적 Evidence catalog를 생성한다.

## 선행 조건

UOW-01과 UOW-05 완료.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/port/out/EvidencePort.java`
- `src/main/java/io/atworks/apiintelligence/adapter/javaparser/SourceEvidenceAdapter.java`
- `src/main/java/io/atworks/apiintelligence/adapter/javaparser/SourceSnippetReader.java`
- 해당 Evidence 테스트와 fixture

## 필수 계약

- catalog는 단일 apiId에 귀속되고 다른 API Evidence를 포함하지 않는다.
- snippet은 source location의 시작/종료 line 밖을 읽지 않는다.
- path는 workspace 상대 `/` 경로다.
- graphNodeIds는 실제 graph node만 참조한다.
- 같은 apiId/location/kind는 동일 Evidence ID를 가진다.
- 중복 제거 후 evidenceId 순으로 정렬한다.
- 파일 없음, UTF-8 읽기 실패, range 오류는 `EVIDENCE_BUILD_FAILED`
- 전체 저장소나 무관한 주변 코드를 Evidence에 포함하지 않는다.

## 구현 절차

1. Evidence 대상 graph node kind를 명시한다.
2. normalized source path containment를 확인한다.
3. UTF-8 line reader로 정확한 range를 읽는다.
4. Evidence ID와 graph node linkage를 만든다.
5. 중복 제거, 정렬 및 무결성을 검증한다.

## 테스트와 완료 조건

- LF/CRLF와 한국어 UTF-8
- 정확한 start/end와 byte-for-byte 기대 snippet
- duplicate, missing file, out-of-range
- 다른 API Evidence 혼입 거부
- Evidence가 실제 graph node와 source만 참조

