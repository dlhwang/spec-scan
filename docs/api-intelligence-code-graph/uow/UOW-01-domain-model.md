# UOW-01 독립 도메인 모델과 결정적 ID

## Goal

기존 `io.atworks.specscan`에 의존하지 않는 API Intelligence 도메인 모델과 불변식을 완성한다.

## 선행 조건

없음.

## 범위와 산출물

- source: `AnalysisSource`, `GitSource`, `LocalSource`, `RevisionType`, `SourceLocation`
- API: `DiscoveredApi`, request binding, response type
- graph: `CodeGraph`, `CodeNode`, `CodeEdge`, node/edge kind
- Evidence: `Evidence`
- Intelligence: `ApiIntelligence`, `IntelligenceItem`
- run: run/API status, result, diagnostic
- UTF-8 SHA-256 기반 API ID와 Evidence ID 생성기

JavaParser, JGit, HTTP, 파일 저장, YAML 및 OpenAI는 범위 밖이다.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/domain/**`
- `src/test/java/io/atworks/apiintelligence/domain/**`

## 필수 계약

- `io.atworks.specscan` import 금지
- API ID 입력: 대문자 method, 정규화 path, controller FQCN, handler signature
- Evidence ID 입력: apiId, `/` 상대 경로, 시작/종료 line, kind
- collection 방어 복사와 stable ordering
- node ID 중복 및 dangling edge 거부
- source line은 1부터 시작하며 range 역전 금지
- Intelligence의 description, category, evidenceIds는 비어 있을 수 없음
- confidence는 유한한 0..1 값
- 결과가 없는 category는 빈 배열

## 구현 절차

1. normalized path와 source location을 만든다.
2. source, API, graph, Evidence, Intelligence, diagnostic/run 모델을 만든다.
3. 생성자에서 불변식과 collection 정렬을 적용한다.
4. 결정적 ID 생성기를 구현한다.
5. 기술 라이브러리 타입이 도메인에 들어오지 않는지 확인한다.

## 테스트와 완료 조건

- 동일 입력 ID 결정성, path/method 정규화, 입력 순서 비의존성
- duplicate node, dangling edge, 잘못된 range 거부
- 빈 필드와 NaN/infinity/범위 밖 confidence 거부
- 모든 도메인 테스트 통과 및 `specscan` import 없음

