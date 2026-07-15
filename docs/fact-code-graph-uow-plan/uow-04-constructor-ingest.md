# UoW 04 — 생성자 Fact ingest

## 목적

객체 생성, 생성자, `super(...)`, `this(...)` 호출을 Fact Code Graph에 적재한다.

## 추가 Fact 후보

- `OBJECT_CREATION`
- `CONSTRUCTOR`
- `CONSTRUCTOR_ARGUMENT`
- `SUPER_CONSTRUCTOR_INVOCATION`
- `THIS_CONSTRUCTOR_INVOCATION`

## 주요 경로

```text
PropertySearch.to
→ new LongRange
→ LongRange.<init>
→ Range.<init>
```

```text
Property.newInstance
→ Property.<init>
→ Property.validate
```

## 주요 대상

- `DefaultFactCodeGraphBuilder`
- `FactMethodVisitor`
- `FactExpressionVisitor`
- `FactNodeType`, `FactEdgeType`, `FactNodePayload`
- `TypeResolver`

## 완료 조건

- 생성자 argument origin이 보존된다.
- 상위 생성자까지 source method로 연결된다.
- 해석 실패 시 잘못된 target edge를 만들지 않는다.

## 테스트

- 직접 생성자, overloaded constructor
- static factory 내부 생성자
- `super(...)`, `this(...)`
- 외부 생성자 해석 실패

