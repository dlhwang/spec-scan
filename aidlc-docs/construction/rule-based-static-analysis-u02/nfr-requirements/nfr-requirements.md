# Unit 02 후보와 해석 모델 NFR Requirements

## 범위

`PredicateCandidate`, `BusinessRuleCandidate`, constraint, evidence, diagnostic, 결정적 ID와 `CandidateResolutionResult` 무결성 검증에 적용한다. 기존 `ValidationCandidate`, `ApiCondition`, `NormalizedResult`에는 동작 변경을 요구하지 않는다.

## 성능 및 용량

### NFR-CM-01 선형 후보 생성

구조 후보 생성은 Fact Graph condition 수에 대해 선형이어야 한다. 의미 후보 생성은 실제 Rule 평가 결과 수에 대해 선형이어야 하며 전체 후보 집합을 반복 중첩 검색하지 않는다.

### NFR-CM-02 불필요한 후보 증폭 금지

- Fact condition 하나당 구조 후보는 정확히 하나다.
- 의미 후보는 실제 Rule 평가 또는 명시적인 미해석·비비즈니스 결과에 대해서만 생성한다.
- 동일 `predicateCandidateId + ruleId` 조합의 중복 결과를 허용하지 않는다.

초기 PoC에서는 wall-clock SLA를 강제하지 않고 fixture별 후보 수와 처리 시간을 검증 Evidence에 기록한다.

## 신뢰성 및 부분 성공

### NFR-CM-03 상태 불변식 강제

도메인 생성 또는 aggregate 검증에서 다음 잘못된 조합을 거부해야 한다.

- `RESOLVED` semantic에 null 또는 blank `ruleId`
- `RESOLVED` semantic에 category `UNKNOWN`
- `RESOLVED` target에 null 또는 blank `targetPath`
- `NOT_APPLICABLE` 또는 `UNRESOLVED` target에 non-null `targetPath`
- 실패·불확실 상태에 빈 diagnostic 목록
- 빈 evidence 목록

### NFR-CM-04 조건 단위 실패 격리

하나의 조건에서 candidate 생성이나 검증이 실패해도 다른 조건의 유효 후보를 손상시키지 않는다. 실패 원인은 aggregate diagnostic으로 남긴다.

### NFR-CM-05 Partial Fact 보존

Fact Graph의 type resolution 실패나 truncation은 가능한 구조 후보를 제거하는 이유가 아니다. `PARTIAL` 또는 관련 diagnostic을 사용해 불확실성을 공개한다.

### NFR-CM-06 참조 무결성

- 모든 의미 후보는 같은 결과에 존재하는 구조 후보를 참조한다.
- 모든 Evidence node ID는 입력 Fact Graph의 node를 참조한다.
- predicate와 business rule candidate ID 중복은 각각 0건이어야 한다.

## 결정성 및 재현성

### NFR-CM-07 결정적 Candidate ID

동일 `graphId`, `conditionNodeId`, `ruleId`, semantic status에서 반복 생성한 candidate ID는 동일해야 한다. collection iteration 순서나 JVM 실행에 의존하지 않는다.

### NFR-CM-08 안정적인 결과 정렬

직렬화 또는 snapshot 비교가 필요한 경계에서는 candidate ID 기준의 안정적인 정렬을 제공한다. aggregate의 의미가 입력 collection 순서에 의존하지 않아야 한다.

### NFR-CM-09 Immutable Result

candidate, constraint, result의 collection은 생성 시 방어적으로 복사한다. 생성 후 원본 목록 변경이 결과를 바꾸지 않아야 한다.

## 추적성 및 관찰 가능성

### NFR-CM-10 Evidence 완전성

모든 후보는 최소 하나의 evidence를 가지며 다음을 통해 원본으로 역추적할 수 있어야 한다.

- Fact node ID
- repository 기준 상대 경로
- source range
- evidence role

### NFR-CM-11 Diagnostic Code 안정성

최소 다음 code를 안정된 계약으로 제공한다.

- `PREDICATE_PARTIAL`
- `PREDICATE_UNSUPPORTED`
- `RULE_UNRESOLVED`
- `NOT_BUSINESS_RULE`
- `TARGET_UNRESOLVED`
- `EVIDENCE_MISSING`
- `DANGLING_PREDICATE_REFERENCE`
- `DANGLING_EVIDENCE_REFERENCE`
- `DUPLICATE_CANDIDATE_ID`
- `INVALID_STATUS_COMBINATION`

### NFR-CM-12 미해석 정보의 투명성

null과 빈 목록은 문서화된 의미대로만 사용한다. serializer 또는 adapter가 이를 임의의 `GENERIC`, 빈 문자열, 추측값으로 치환하지 않는다.

## 보안 및 안전

### NFR-CM-13 Static Analysis Only

후보 생성과 검증은 대상 repository의 build, test, run, reflection 기반 class loading 또는 임의 script를 실행하지 않는다.

### NFR-CM-14 Source Evidence 최소화

Evidence에는 필요한 source range와 제한된 snippet만 보존한다. 전체 소스 파일이나 workspace 절대 경로를 candidate 또는 diagnostic에 복제하지 않는다.

## 테스트 품질

### NFR-CM-15 Example-Based Tests

다음 대표 상태 조합을 JUnit assertion으로 고정한다.

- 완전히 해석된 target constraint
- Rule 미매칭 `EXTRACTED + UNRESOLVED`
- type resolution 실패 `PARTIAL`
- parser 지원 밖 `UNSUPPORTED`
- semantic resolved와 target unresolved 조합
- 명시적인 `NOT_BUSINESS_RULE`
- `UNKNOWN`과 `OTHER` 허용 범위
- dangling predicate/evidence reference

### NFR-CM-16 Property-Based Tests

jqwik를 사용해 최소 다음 property를 검증한다.

- 동일 canonical input은 동일 candidate ID를 생성한다.
- 서로 다른 condition node 또는 ruleId는 다른 ID를 생성한다.
- 유효 상태 조합은 생성 가능하고 금지된 조합은 거부된다.
- 모든 유효 candidate의 evidence는 비어 있지 않다.
- 외부 collection 변경은 생성된 결과를 바꾸지 않는다.
- 입력 순서 변경은 정규화된 candidate identity 집합을 바꾸지 않는다.

### NFR-CM-17 재현 가능한 PBT

- jqwik shrinking을 유지한다.
- 실패 시 seed와 최소 failing input을 출력한다.
- property는 일반 Gradle test task에 포함한다.
- 발견된 결함은 축소 입력의 example-based regression test로 추가한다.

## PBT Compliance

| Rule | 상태 | 적용 내용 |
|:---|:---|:---|
| PBT-01 | Compliant | candidate ID와 상태 조합 invariant 식별 |
| PBT-02 | Planned | status, category, target 조합 generator 사용 |
| PBT-03 | Planned | 참조·evidence·immutability invariant 검증 |
| PBT-04 | Planned | 동일 canonical input의 idempotency 검증 |
| PBT-05 | N/A | 별도 reference algorithm이 없음 |
| PBT-06 | N/A | mutable state machine이 아닌 immutable 모델 |
| PBT-07 | Planned | domain-specific candidate generator 제공 |
| PBT-08 | Planned | shrinking과 seed 재현 유지 |
| PBT-09 | Compliant | 승인된 jqwik 1.7.4 재사용 |
| PBT-10 | Compliant | example-based test와 PBT 병행 |

## 초기 수용 기준

- 대표 상태 조합 example test 전체 통과
- PBT property 전체 통과
- 동일 입력 반복 시 candidate ID 집합 일치
- duplicate ID와 dangling reference 0건
- 유효 후보의 evidence 누락 0건
- 실패·불확실 상태의 diagnostic 누락 0건
- 기존 전체 test suite regression 0건
