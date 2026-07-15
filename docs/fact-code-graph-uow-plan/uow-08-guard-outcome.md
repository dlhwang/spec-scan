# UoW 08 — Guard outcome과 HTTP 실패

## 목적

조건 위반이 실제 실패 응답으로 이어지는 경우만 PreCondition 후보로 인정한다.

## 목표 경로

```text
CONDITION
→ THEN_OUTCOME 또는 ELSE_OUTCOME
→ THROW/RETURN
→ EXCEPTION_HANDLER
→ HTTP_STATUS
```

## 지원 대상

- 직접 `throw`
- `orElseThrow`
- 성공 분기 이후 후속 실패
- 전역 `@ExceptionHandler`
- 조기 return

## 규칙

- 단순 `if`가 있다는 이유만으로 조건을 출력하지 않는다.
- catch에서 복구되는 예외는 실패 조건으로 확정하지 않는다.
- 모든 정상 경로와 실패 경로를 구분한다.

## 완료 조건

- `IllegalArgumentException → 400` 경로가 연결된다.
- `DataNotFoundException` handler가 연결된다.
- 실패 결과가 없는 조건은 executable condition이 되지 않는다.

## 테스트

- then/else throw
- trailing failure
- catch 복구
- handler 없는 예외

