# UOW-12 API Intelligence UI

## Goal

기존 화면과 상태를 공유하지 않는 신규 메뉴에서 실행, 진행, API별 결과, Evidence 및 Raw JSON을 읽기 쉽게 제공한다.

## 선행 조건

UOW-11 완료.

## 허용 파일

- 신규 `src/main/resources/static/intelligence.html`
- 신규 `src/main/resources/static/intelligence.js`
- 최소 수정 `src/main/resources/static/index.html`
- 최소 수정 `src/main/resources/static/style.css`
- 가능한 UI/정적 resource smoke 테스트

## 화면 계약

- top navigation: `Spec Scan`(`/`)와 `API Intelligence`(`/intelligence.html`)
- LOCAL/GIT 선택과 조건부 path/url/revision 입력
- 실행 전에 source code가 OpenAI로 전송되고 API 수만큼 호출·비용이 발생할 수 있음을 고지
- 실행 요약: phase, 전체/완료/성공/실패, elapsed, output directory
- API 목록: method, path, handler, status, category counts
- 검색: method/path/controller; 필터: method/status
- API 상세: Evidence, PreCondition, ResponseAssertion, BusinessRule, Exception, Additional Analysis
- Intelligence Evidence ID 선택 시 같은 API source Evidence 강조
- 실패 API: code, safe message, retryable
- 전체 Raw JSON `<details>`와 copy
- code graph는 표시하지 않음

## 상태와 polling 계약

- view: form/running/result/fatalError
- selected API는 array index가 아닌 apiId
- 새 실행 시 이전 result를 제거하고 중복 submit 방지
- POST 후 1초 polling
- network 오류 backoff 2/4/8초
- terminal status에서 polling 중단 후 result fetch
- API 발견 전 indeterminate, 이후 completed/total progress

## 보안과 접근성

- path, snippet, LLM 문자열, 오류는 모두 untrusted
- 외부 문자열은 `textContent` 또는 안전 DOM API로만 렌더링
- current nav `aria-current=page`
- label과 field error를 `aria-describedby`로 연결
- 진행 `role=status`, fatal error `role=alert`
- 색상 외 텍스트 상태, keyboard focus, reduced-motion

## 테스트와 완료 조건

- source form 전환/검증, duplicate submit 방지
- polling backoff와 terminal stop
- apiId selection, 검색/필터, Evidence highlight
- malicious HTML이 DOM으로 실행되지 않음
- empty category 표현과 Raw JSON copy
- 기존 `/` 화면과 `app.js` 상태 회귀 없음

