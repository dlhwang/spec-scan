# Stage 02 Critic Review

## Result

OKAY

## 검토 항목

### 범용성

detector는 typed `CONTROLS`, `THEN_OUTCOME`, `ELSE_OUTCOME` edge와 node type을 사용한다. Rule engine은 ruleId와 SPI만 다루며 service, repository, domain 이름을 하드코딩하지 않는다.

### False Positive

기본 detector는 throw만 인정하고 return은 policy 없이는 제외한다. 중첩 branch의 outcome이 상위 condition에 직접 연결되는 upstream 결함을 함께 수정한다.

### Identity와 Dedup 충돌

Unit 02 ID가 Evidence를 포함하지 않아 Unit 03 exact dedup key와 불일치했다. 기존 overload를 유지하고 Evidence fingerprint overload를 추가해 다른 Evidence 후보가 aggregate duplicate ID로 손실되지 않게 한다.

### Failure Isolation

Rule invocation boundary가 exception, null list, null item과 invalid candidate를 개별 실패로 변환한다. fatal JVM error까지 포괄 catch하지 않는다.

### 과도한 구현

구체 Rule, target resolver, output adapter와 concurrency는 제외됐다. engine core와 검증 support로 범위가 제한된다.

## Required Corrections Applied

- Rule pack 간 duplicate ruleId는 registry 구성 시 거부하도록 명시했다.
- precedence가 exact duplicate 제거 이후에 적용되도록 순서를 고정했다.
- 모든 Rule 실패 시에도 predicate별 unresolved fallback 하나를 생성하도록 검증 항목을 추가했다.
