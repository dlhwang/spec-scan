# UOW-2-2: THROW 예외 표현식 연결

## Goal (목표)
`ThrowStmt`에 사용된 예외 객체 생성 식(`ObjectCreationExpr` 등)을 분석하고, 예외 타입, 생성자 인자, 오류 코드 등의 세부 의미 단위를 하위 그래프로 재귀 연결하여 의미 지원을 완성한다.

## 1. 문제 증거
현재 `FactMethodVisitor`의 `outcome` 메서드는 단순히 `ThrowStmt` 노드만 생성할 뿐, `throw` 뒤에 붙은 예외 표현식을 방문하지 않습니다.
```java
throw new InvalidParameterException(ErrorCode.INVALID_USER_ID, userId);
```
위와 같은 코드가 있을 때, 예외 객체 생성 인자인 `ErrorCode.INVALID_USER_ID`와 `userId`가 `THROW` 노드와 연결되지 않아 검증 대상 필드 및 에러 코드 분석이 불가능합니다.

## 2. 지원 대상 코드 패턴
```java
throw new IllegalArgumentException("invalid value: " + userId);
```

## 3. 현재 그래프
```text
THROW (IllegalArgumentException) -- (예외 표현식 하위 그래프 단절)
```

## 4. 기대 그래프
```text
THROW
  └─ OPERAND_OF → OBJECT_CREATION(IllegalArgumentException)
                     └─ ARGUMENT → BINARY_VALUE("+")
                                      ├─ LEFT → "invalid value: "
                                      └─ RIGHT → FIELD_ACCESS(userId)
```

## 5. 수정 책임 클래스
* [FactMethodVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java)
* [FactExpressionVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactExpressionVisitor.java)

## 6. 비대상 범위
* `ExceptionFactory` 같은 팩토리 메서드 호출을 통한 예외 생성 지원은 본 UOW의 대상에서 제외하고, 기본 `new` 생성자로 제한합니다. (팩토리 패턴은 추후 필요시 확장)

## 7. 테스트 케이스
* `throw new SomeException(arg1, arg2)` 코드 파싱 후, `THROW` 노드로부터 `OBJECT_CREATION` 노드를 거쳐 파라미터 변수로의 경로가 유효한지 검증.

## 8. 완료 기준
* 모든 `ThrowStmt` 노드는 `THROW → 반환 표현식 루트` 관계를 가져야 합니다.
* 예외 생성자 인자에 포함된 변수 및 리터럴을 그래프 경로 탐색을 통해 찾아낼 수 있어야 합니다.

## 9. 성능 및 호환성 영향
* 표현식 트리를 깊게 순회하므로 예외가 많이 정의된 메서드에 대해 미세한 탐색 비용이 발생할 수 있으나 유의미한 수준은 아닙니다.
