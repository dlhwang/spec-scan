# UoW 05 — Lambda 및 method reference

## 목적

stream 내부 lambda와 method reference에서 실행되는 도메인 호출을 추적한다.

## 지원 대상

- expression lambda
- block lambda
- static method reference
- instance method reference
- collection element에서 lambda parameter로의 흐름

## 목표 경로

```text
PropertySave.to
→ lambda
→ ContractDetail.newInstance
→ ContractDetail.<init>
→ isNotValid
```

```text
PropertyModifyService.modify
→ ContractDetailVO::to
→ ContractDetail.newInstance
```

## 완료 조건

- enclosing method에서 lambda body로 도달 가능하다.
- method reference가 실제 source method와 연결된다.
- 외부 라이브러리 stream 구현 내부는 추적하지 않는다.

## 테스트

- expression/block lambda
- static/instance method reference
- target resolution 실패
- 무관한 method reference 오탐 방지

