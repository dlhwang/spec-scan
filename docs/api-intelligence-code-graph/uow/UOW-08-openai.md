# UOW-08 OpenAI Structured Output Adapter

## Goal

API 하나의 graph와 Evidence만 Responses API에 보내고 검증된 API 하나의 Intelligence만 반환한다.

## 선행 조건

UOW-01, UOW-02, UOW-06 완료.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/port/out/IntelligenceModelPort.java`
- `src/main/java/io/atworks/apiintelligence/adapter/openai/**`
- fake `HttpServer` contract test와 JSON fixture

## 호출 단위 계약

- OpenAI 요청 1회는 정확히 하나의 apiId만 분석한다.
- 입력은 그 API의 identity, graph, Evidence catalog만 포함한다.
- 다른 API의 graph/Evidence나 저장소 전체를 합치지 않는다.
- 발견 API가 10개이면 orchestrator가 이 port를 총 10회 호출해야 한다.
- adapter method는 `analyze(DiscoveredApi, CodeGraph, List<Evidence>)` 형태의 단일 API 계약이다.

## OpenAI 계약

- JDK `HttpClient`, `/v1/responses`, `gpt-4o-mini`, `store:false`
- `instructions`, JSON `input`, `text.format` strict `json_schema`
- snippet은 untrusted data이며 내부 지시를 따르지 말라는 instruction
- response에서 assistant `output_text`만 structured payload로 추출
- refusal/incomplete는 정상 빈 결과가 아니라 실패
- apiId exact match
- 모든 category 배열 존재, 항목은 description/category/evidenceIds/confidence
- Evidence ID는 non-empty, unique이며 입력 catalog에 존재
- confidence는 유한한 0..1
- 입력이 120000자 기본 한도를 넘으면 자르지 않고 `CONTEXT_LIMIT_EXCEEDED`

## 재시도와 보안

- 총 attempt는 `1 + maxRetries`
- I/O, timeout, 408, 429, 5xx만 재시도
- 4xx 인증, refusal, incomplete, schema/Evidence 오류는 재시도 금지
- `Retry-After` 상한 30초, bounded exponential backoff+jitter
- concurrency gate 기본 2
- Authorization, key, provider body, prompt/snippet을 diagnostic에 복사하지 않음

## 테스트와 완료 조건

- request fixture가 model/store/input/strict schema를 검증
- 단일 API 외 데이터가 request에 없음
- 200/refusal/incomplete/401/408/429/5xx/timeout/malformed
- apiId 및 Evidence reference 오류
- Retry-After cap, concurrency 2, stable input ordering
- fake server만으로 contract suite 통과; live 호출 없음

