# UoW 12 — 응답 DTO data flow

## 목적

정상 반환값이 응답 DTO 필드에 전달되는 경로를 Fact Code Graph에 적재한다.

## 목표 경로

```text
RETURN
→ ResponseEntity body
→ response DTO factory/builder
→ response field
→ source field
```

## 지원 범위

- builder setter
- constructor와 record constructor
- static `to(...)` mapper
- nested DTO
- collection mapping

## 중요 규칙

값이 필드에 대입됐다는 사실만으로 `NOT_NULL`을 생성하지 않는다. 이 UoW는 data flow만 제공한다.

## 완료 조건

- `Property.id → response.propertyId` 경로가 존재한다.
- collection mapping의 element origin이 유지된다.
- 누락되거나 조건부 대입된 필드를 구분한다.

## 테스트

- builder/constructor/static mapper
- collection mapper
- null 대체
- 일부 필드 미매핑

