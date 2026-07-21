# UOW-11 HTTP API와 안전한 Bootstrap

## Goal

기존 `/api/scan`을 변경하지 않고 신규 비동기 API와 fail-soft bootstrap을 loopback 서버에 노출한다.

## 선행 조건

UOW-02와 UOW-10 완료.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/web/**`
- `src/main/java/io/atworks/apiintelligence/config/ApiIntelligenceBootstrap.java`
- `src/test/java/io/atworks/apiintelligence/web/**`
- 최소 수정 `src/main/java/io/atworks/specscan/SpecScanWebServer.java`

## Route 계약

- `POST /api/intelligence/runs` → 202 `{runId,statusUrl,resultUrl}`
- `GET /api/intelligence/runs/{runId}` → immutable 진행 snapshot
- `GET /api/intelligence/runs/{runId}/result` → COMPLETED/PARTIAL aggregate

POST body는 다음 중 하나다.

```json
{"source":{"type":"LOCAL","path":"D:/workspace/project"}}
```

```json
{"source":{"type":"GIT","url":"https://host/repo.git","revisionType":"BRANCH","revision":"main"}}
```

## 보안과 오류 계약

- server bind 기본값과 유일 지원값은 `127.0.0.1`
- POST는 `application/json`, body 최대 64 KiB
- Origin이 있으면 server origin과 일치
- CORS header 제공 금지
- 400 입력/Content-Type/Origin, 404 run 없음, 409 미완료 result, 413 body 초과, 429 capacity, 500 최종화/예상 밖 오류
- 공통 오류 `{code,message,field?,runId?}`
- stack trace, key, provider body, source snippet 반환 금지
- configuration/bootstrap 실패는 신규 handler만 degraded; 기존 서버와 `/api/scan` 시작 유지
- HTTP executor에서 분석/LLM 작업 실행 금지

## 구현 절차

1. request/response/error DTO와 mapper를 만든다.
2. body limit, JSON, Origin 검증을 구현한다.
3. 세 route를 use case에 위임한다.
4. independent bootstrap과 background executor/shutdown hook을 만든다.
5. `SpecScanWebServer`에는 context 등록과 loopback bind만 최소 반영한다.

## 테스트와 완료 조건

- 세 route 정상/오류 계약
- malformed, oversize, Content-Type, Origin
- not found/not finished/capacity/degraded
- HTTP thread가 장기 작업을 수행하지 않음
- `/api/scan` regression과 API Intelligence 모델 독립성

