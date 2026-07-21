# UOW-13 End-to-End 검증과 Construction 완료

## Goal

독립 fixture와 RealEstate smoke test로 요구사항 합격 기준을 입증하고 Construction 체크리스트를 완료한다.

## 선행 조건

UOW-01부터 UOW-12까지 완료.

## 허용 파일

- `src/test/resources/api-intelligence/fixture/**`
- `src/test/java/io/atworks/apiintelligence/e2e/**`
- 필요한 경우 opt-in Gradle test task의 최소 변경
- `docs/api-intelligence-code-graph/construction.md`

production 코드는 이 UoW에서 수정하지 않는다. 실패가 production 결함이면 해당 소유 UoW로 되돌아간다.

## 검증 범위

- 독립 Spring Boot 3.x/Spring MVC fixture의 모든 지원 annotation
- LOCAL 전체 pipeline
- test Git repository의 URL/revision pipeline
- fake OpenAI를 통한 단일 API별 호출과 부분 성공
- `gradle runWeb` route/static smoke
- 기존 `/api/scan` regression
- `D:\workspace\real-estate\RealEstate` read-only smoke
- 명시적 opt-in `gpt-4o-mini` live test
- 저장 graph/evidence/intelligence/result/run JSON 직접 검사
- log/UI/error/artifact secret scan

## 필수 계약

- RealEstate 경로는 test input에만 사용하고 production에 하드코딩하지 않는다.
- live test는 API key 존재와 명시적 opt-in 조건을 모두 만족할 때만 실행한다.
- live 결과의 자연어 문구를 golden assertion하지 않는다.
- schema, Evidence ID 무결성, 단일 API 요청 경계, 호출 횟수, 부분 성공을 검증한다.
- API 10개 fixture에서는 OpenAI adapter 호출이 10회이며 각 request가 하나의 apiId만 포함해야 한다.
- RealEstate 실행 전에 fake 기반 E2E와 secret gate가 통과해야 한다.

## 실행 절차

1. `construction.md`에 UOW-01~13별 5개 기본 checkbox를 생성한다.
2. 전체 unit/integration/E2E test를 실행한다.
3. runWeb와 기존 scan regression을 확인한다.
4. RealEstate local smoke와 artifact를 확인한다.
5. 사용자 key가 구성된 경우에만 live test를 opt-in 실행한다.
6. 요구사항 합격 기준 17개에 명령, test 또는 artifact 경로를 연결한다.

## 완료 조건

- [ ] Contract/model implemented
- [ ] Unit tests implemented
- [ ] Focused tests passed
- [ ] Integration/regression impact verified
- [ ] UoW acceptance/DoD met
- [ ] 요구사항 합격 기준 17개의 검증 증거 기록
- [ ] 미완료 항목과 사유가 숨김없이 Construction 문서에 남음

