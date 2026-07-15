# 사용자 YAML 룰 작성 예시

## 1. 프로젝트 정책 검사

### Java 코드

```java
if (!projectPolicy.validateProjectPolicy(project)) {
    throw new InvalidProjectException();
}
```

### YAML

```yaml
rules:
  - id: PROJECT_POLICY_VALIDATION
    match:
      predicateType: BOOLEAN_CALL
      methodName: validateProjectPolicy
      failureOutcome: THEN
    output:
      category: INVARIANT
      constraint:
        kind: CONTROL_FLOW_ONLY
```

### NEW_ONLY 출력

```json
{
  "requestPreconditions": [],
  "responseAssertions": [],
  "excludedBusinessRules": [
    {
      "ruleId": "PROJECT_POLICY_VALIDATION",
      "category": "INVARIANT",
      "constraintKind": "CONTROL_FLOW_ONLY",
      "reasonCode": "TARGET_UNRESOLVED"
    }
  ]
}
```

## 2. 문서 수정 권한 검사

### Java 코드

```java
if (!permissionService.canEdit(user, document)) {
    throw new AccessDeniedException();
}
```

### YAML

```yaml
rules:
  - id: DOCUMENT_EDIT_PERMISSION
    match:
      predicateType: BOOLEAN_CALL
      methodName: canEdit
      failureOutcome: THEN
    output:
      category: AUTHORIZATION
      constraint:
        kind: CONTROL_FLOW_ONLY
```

### NEW_ONLY 출력

```json
{
  "excludedBusinessRules": [
    {
      "ruleId": "DOCUMENT_EDIT_PERMISSION",
      "category": "AUTHORIZATION",
      "constraintKind": "CONTROL_FLOW_ONLY",
      "reasonCode": "TARGET_UNRESOLVED"
    }
  ]
}
```

## 3. 주문 수정 가능 상태 검사

### Java 코드

```java
if (orderPolicy.isEditable(order)) {
    updateOrder(order);
} else {
    throw new InvalidOrderStateException();
}
```

### YAML

```yaml
rules:
  - id: ORDER_EDITABLE_STATE
    match:
      predicateType: BOOLEAN_CALL
      methodName: isEditable
      failureOutcome: ELSE
    output:
      category: STATE_PRECONDITION
      constraint:
        kind: CONTROL_FLOW_ONLY
```

### NEW_ONLY 출력

```json
{
  "excludedBusinessRules": [
    {
      "ruleId": "ORDER_EDITABLE_STATE",
      "category": "STATE_PRECONDITION",
      "constraintKind": "CONTROL_FLOW_ONLY",
      "reasonCode": "TARGET_UNRESOLVED"
    }
  ]
}
```

## 4. 이메일 중복 검사

### Java 코드

```java
if (memberPolicy.isEmailDuplicated(email)) {
    throw new DuplicateEmailException();
}
```

### YAML

```yaml
rules:
  - id: DUPLICATE_MEMBER_EMAIL
    match:
      predicateType: BOOLEAN_CALL
      methodName: isEmailDuplicated
      failureOutcome: THEN
    output:
      category: UNIQUENESS
      constraint:
        kind: CONTROL_FLOW_ONLY
```

### NEW_ONLY 출력

```json
{
  "excludedBusinessRules": [
    {
      "ruleId": "DUPLICATE_MEMBER_EMAIL",
      "category": "UNIQUENESS",
      "constraintKind": "CONTROL_FLOW_ONLY",
      "reasonCode": "TARGET_UNRESOLVED"
    }
  ]
}
```

## 5. 같은 이름의 메서드 구분하기

### Java 코드

```java
if (!projectPolicy.validateProjectPolicy(project)) {
    throw new InvalidProjectException();
}
```

### YAML

```yaml
rules:
  - id: PROJECT_POLICY_VALIDATION
    match:
      predicateType: BOOLEAN_CALL
      methodName: validateProjectPolicy
      resolvedSignatureContains: ProjectPolicy.validateProjectPolicy
      failureOutcome: THEN
    output:
      category: INVARIANT
      constraint:
        kind: CONTROL_FLOW_ONLY
```

## 6. 여러 룰을 한 파일에 작성하기

```yaml
rules:
  - id: DOCUMENT_EDIT_PERMISSION
    match:
      predicateType: BOOLEAN_CALL
      methodName: canEdit
      failureOutcome: THEN
    output:
      category: AUTHORIZATION
      constraint:
        kind: CONTROL_FLOW_ONLY

  - id: ORDER_EDITABLE_STATE
    match:
      predicateType: BOOLEAN_CALL
      methodName: isEditable
      failureOutcome: ELSE
    output:
      category: STATE_PRECONDITION
      constraint:
        kind: CONTROL_FLOW_ONLY
```

## 7. `requestPreconditions` 반영 예시

사용자 YAML의 `CONTROL_FLOW_ONLY` 룰은 현재 `requestPreconditions`로 변환되지 않는다. 다음과 같은 요청 바인딩과 구조 조건이 `requestPreconditions`에 들어간다.

### Java 코드

```java
public record CreateMemberRequest(
    @NotBlank String name,
    @Email String email
) {}

@PostMapping("/members")
public ResponseEntity<MemberResponse> create(
    @RequestBody CreateMemberRequest request,
    @RequestParam(required = true) String tenantId
) {
    return ResponseEntity.ok(memberService.create(request, tenantId));
}
```

### NEW_ONLY 출력

```json
{
  "requestPreconditions": [
    {
      "targetLocation": "BODY",
      "targetPath": "$",
      "operator": "REQUIRED",
      "expected": "true",
      "ruleId": "REQUEST_BINDING_REQUIRED"
    },
    {
      "targetLocation": "QUERY",
      "targetPath": "$.tenantId",
      "operator": "REQUIRED",
      "expected": "true",
      "ruleId": "REQUEST_BINDING_REQUIRED"
    }
  ]
}
```

## 8. `responseAssertions` 반영 예시

### Java 코드

```java
@PostMapping("/members")
public ResponseEntity<MemberResponse> create(@RequestBody CreateMemberRequest request) {
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .header("X-Result", "created")
        .body(memberService.create(request));
}
```

### NEW_ONLY 출력

```json
{
  "responseAssertions": [
    {
      "targetLocation": "STATUS",
      "targetPath": "$status",
      "operator": "EQUALS",
      "expected": "201",
      "ruleId": "RESPONSE_STATUS_METADATA"
    },
    {
      "targetLocation": "RESPONSE_HEADER",
      "targetPath": "$.X-Result",
      "operator": "EQUALS",
      "expected": "created",
      "ruleId": "RESPONSE_HEADER_METADATA"
    }
  ]
}
```

## 9. 세 출력 필드 한 번에 보기

### Java 코드

```java
@PostMapping("/documents")
public ResponseEntity<DocumentResponse> create(
    @RequestBody CreateDocumentRequest request,
    @RequestParam String tenantId
) {
    if (!permissionService.canCreate(tenantId)) {
        throw new AccessDeniedException();
    }

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .header("X-Document-State", "created")
        .body(documentService.create(request));
}
```

### YAML

```yaml
rules:
  - id: DOCUMENT_CREATE_PERMISSION
    match:
      predicateType: BOOLEAN_CALL
      methodName: canCreate
      failureOutcome: THEN
    output:
      category: AUTHORIZATION
      constraint:
        kind: CONTROL_FLOW_ONLY
```

### NEW_ONLY 출력

```json
{
  "requestPreconditions": [
    {
      "targetLocation": "BODY",
      "targetPath": "$",
      "operator": "REQUIRED",
      "expected": "true",
      "ruleId": "REQUEST_BINDING_REQUIRED"
    },
    {
      "targetLocation": "QUERY",
      "targetPath": "$.tenantId",
      "operator": "REQUIRED",
      "expected": "true",
      "ruleId": "REQUEST_BINDING_REQUIRED"
    }
  ],
  "responseAssertions": [
    {
      "targetLocation": "STATUS",
      "targetPath": "$status",
      "operator": "EQUALS",
      "expected": "201",
      "ruleId": "RESPONSE_STATUS_METADATA"
    },
    {
      "targetLocation": "RESPONSE_HEADER",
      "targetPath": "$.X-Document-State",
      "operator": "EQUALS",
      "expected": "created",
      "ruleId": "RESPONSE_HEADER_METADATA"
    }
  ],
  "excludedBusinessRules": [
    {
      "ruleId": "DOCUMENT_CREATE_PERMISSION",
      "category": "AUTHORIZATION",
      "constraintKind": "CONTROL_FLOW_ONLY",
      "reasonCode": "TARGET_UNRESOLVED"
    }
  ]
}
```

## 10. 복사용 YAML 템플릿

```yaml
rules:
  - id: RULE_ID
    match:
      predicateType: BOOLEAN_CALL
      methodName: methodName
      failureOutcome: THEN
    output:
      category: OTHER
      constraint:
        kind: CONTROL_FLOW_ONLY
```
