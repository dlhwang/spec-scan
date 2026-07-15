# UoW 11 — 응답 status와 body

## 목적

명시적인 정상 응답 status와 body 유무를 canonical Operator로 출력한다.

## 지원 패턴

- `ResponseEntity.ok(...)`
- `ResponseEntity.noContent().build()`
- `new ResponseEntity<>(body, status)`

## 목표 출력

DELETE 응답:

```text
$status EQ 204
$body EMPTY
```

## 작업 범위

- 정상 controller return과 exception handler return 구분
- 반환 builder 체인의 status와 body 추적
- `response200` 고정 표현의 호환성 검토

## 완료 조건

- DELETE를 200으로 보고하지 않는다.
- body가 없는 응답에 DTO schema/assertion을 만들지 않는다.
- Operator는 `EQ`, `EMPTY` 등 허용 목록만 사용한다.

## 테스트

- ok/noContent/명시 status
- 조건별 다른 status
- 정상 응답과 handler 응답 분리

