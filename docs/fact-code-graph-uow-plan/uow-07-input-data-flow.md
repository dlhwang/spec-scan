# UoW 07 — 입력 data flow

## 목적

도메인 guard의 operand를 API 요청 JSONPath까지 역추적한다.

## 필요한 관계

- `READS_PARAMETER`
- `READS_FIELD`
- `ASSIGNED_FROM`
- `ARGUMENT_OF`
- `RETURNS`
- `DERIVES_FROM`
- `ELEMENT_OF`

## 목표 경로

```text
$.contractDetails[*].deposit
→ ContractDetailDTO.deposit
→ ContractDetailVO.deposit
→ ContractDetail.newInstance argument
→ isNotValid.deposit
→ LTE(deposit, 0)
```

## 작업 범위

- getter와 backing field
- local variable assignment
- constructor와 factory argument
- record component
- collection element wildcard
- 메서드 parameter와 call argument

## 완료 조건

- guard operand를 정확한 request path에 연결한다.
- origin이 여러 개면 ambiguous로 처리한다.
- 필드명 유사도만으로 연결하지 않는다.

## 테스트

- DTO → domain 직접 전달
- local variable 경유
- record와 getter
- collection element
- 같은 이름의 무관한 필드

