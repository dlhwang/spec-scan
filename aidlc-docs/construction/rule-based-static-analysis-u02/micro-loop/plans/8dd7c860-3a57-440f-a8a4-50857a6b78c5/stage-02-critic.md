# Stage 02 Critic Review

## Result

OKAY

## 검토 항목

### 특정 소스 특화 위험

계획은 class, package, method 명명 규칙을 사용하지 않는다. Predicate 분류 입력은 Fact node payload와 typed edge뿐이므로 범용 Java 분석기 목표와 일치한다.

### 상태 손실 위험

`UNKNOWN`과 `OTHER`, `UNRESOLVED`와 `NOT_BUSINESS_RULE`, semantic과 target 상태가 분리된다. 자동 fallback으로 상태를 덮어쓰지 않는 test가 계획에 포함돼 있다.

### 과도한 구현 위험

Rule engine, target resolver, output adapter는 제외됐다. Unit 02에서 필요한 domain 및 construction support로 범위가 제한돼 있다.

### 참조 무결성 위험

단일 record constructor만으로 검증할 수 없는 predicate/evidence reference는 aggregate validator가 담당한다. dangling reference test와 PBT가 포함돼 있다.

### 호환성 위험

신규 package 병렬 도입이며 기존 public record 수정이 없다. 전체 regression과 기존 파일 비변경 확인이 계획에 있다.

## Required Corrections Applied

- 의미 후보 factory가 Rule matching을 실행하지 않는다는 경계를 명시했다.
- Evidence 검증에 입력 Fact node index를 사용해 같은 graph 참조를 강제했다.
- 성공 후보의 빈 diagnostic 허용 test를 포함하고 형식적 INFO 생성은 금지했다.
