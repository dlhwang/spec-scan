# UOW-02 설정 로딩과 비밀정보 경계

## Goal

UTF-8 YAML과 환경 변수에서 설정을 안전하게 읽고 API key 누락 시 degraded 상태를 표현한다.

## 선행 조건

UOW-01 완료.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/config/**`
- `src/test/java/io/atworks/apiintelligence/config/**`
- `src/main/resources/application.yml`
- `application-local.example.yml`
- `.gitignore`
- `build.gradle`은 기존 Jackson YAML로 불가능한 경우만 최소 수정

## 설정 계약

우선순위는 `OPENAI_API_KEY` 환경 변수, 작업 디렉터리 `application-local.yml`, classpath `application.yml` 순이다.

```text
outputRoot=build/api-intelligence-runs
graph.maxDepth=8, maxMethods=100, maxEdges=2000
openai.endpoint=https://api.openai.com/v1/responses
model=gpt-4o-mini
requestTimeout=60s, connectTimeout=10s
maxRetries=2, maxConcurrency=2
retryAfterCap=30s, maxInputCharacters=120000
```

- unknown key와 유효하지 않은 URI/type/range를 거부한다.
- API key가 없어도 load와 서버 시작은 가능하다.
- run 시작 시 `OPENAI_NOT_CONFIGURED`를 판정할 수 있어야 한다.
- API key는 `toString`, JSON, diagnostic 및 exception에 포함하지 않는다.
- 실제 `application-local.yml`과 output 폴더는 Git에서 제외한다.

## 구현 절차

1. immutable configuration과 안전한 configuration status를 정의한다.
2. base와 optional local YAML을 병합한다.
3. 환경 변수 key를 마지막에 적용한다.
4. 비민감 설정을 엄격히 검증한다.
5. key가 빈 example과 ignore 규칙을 추가한다.

## 테스트와 완료 조건

- base/local/env 우선순위와 local 파일 없음
- unknown key, 잘못된 값 및 endpoint
- key 누락 load 성공
- secret 문자열이 출력 경로 어디에도 나타나지 않음
- 실제 key 파일이 추적되지 않음

