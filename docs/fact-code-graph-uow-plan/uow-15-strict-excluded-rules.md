# UoW 15 — Strict excludedBusinessRules

## 목적

그래프에는 있지만 12개 Operator로 실행할 수 없는 유용한 규칙만 제한적으로 출력한다.

## 생성 조건

1. endpoint에서 rule까지 도달 가능하다.
2. condition과 outcome이 Fact로 존재한다.
3. request/response condition으로 정확하게 표현할 수 없다.
4. 등록된 rule template이 있다.
5. evidence path를 제공할 수 있다.

## 초기 허용 후보

- repository resource existence
- runtime 인증 또는 권한 상태

## 초기 비허용 후보

- QueryDSL 의미에 대한 자연어 해석
- 논리 삭제 비대칭에 대한 코드 리뷰 해석
- 전체 교체 또는 중복 제거에 대한 추정
- graph node가 없는 annotation 설명

## 작업 범위

- 자유 텍스트 생성 제거
- rule ID별 고정 template
- graph fact에서만 template parameter 공급
- evidence가 부족하면 빈 배열 유지

## 완료 조건

- graph node 없는 excluded rule이 없다.
- exporter가 자유롭게 비즈니스 의미를 서술하지 않는다.
- 실행 가능 조건은 excluded보다 executable output을 우선한다.

