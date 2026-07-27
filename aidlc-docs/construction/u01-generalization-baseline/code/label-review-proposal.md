# U01 Snapshot Proposal — Label/Disposition Review

> Snapshot: `3a33893686256d843e98d6d75b95319f6e9d36008d40c4ae3bc6d01cc8d049d5`
> Bundle: `build/reports/specscan/u01/u01-3a33893686256d84-snapshot_proposal/`
> State: `APPROVED` — 사용자가 제안 disposition을 승인했으며, 아래 baseline-manifest에 반영되었다.

## 1. 실행 범위와 결과

| 구분 | 결과 |
|---|---:|
| required base corpus | 5개 |
| separated rename/mutation holdout | 1개 |
| available endpoint graph | 36 / 36 |
| semantic resolution | 11 / 25 |
| optional RealEstate | `EXCLUDED_OPTIONAL` |
| analyzer invocation | 6회, corpus별 1회 |

## 2. 제안 disposition

| 그룹 | 건수 | 관찰 | 제안 | 이유 |
|---|---:|---|---|---|
| Spring Data lookup failure | 5 | `SPRING_DATA_FIND_BY_ID_OR_ELSE_THROW`, `EXISTENCE`, `EXISTS` | `PRESERVE` | aggregate와 이름이 달라도 같은 Optional lookup failure로 분류됨 |
| Java null rejection guard | 6 | `JAVA_NULL_REJECTION_GUARD`, `INVARIANT`, `NOT_NULL` | `PRESERVE` | `if (x == null)`과 `Objects.requireNonNull`을 공통 null-rejection 의미로 분류함 |
| generic literal/range/string guards | 13 | 현재 `UNKNOWN/UNRESOLVED` | `REPLACE` | 숫자 범위, blank/length, email 형식 등은 특정 repo 규칙이 아니라 일반 constraint로 기대해야 함 |
| rename/mutation existence holdout | 1 | base는 resolved, `isEmpty → throw → get` holdout은 unresolved | `REPLACE` | 같은 `catalog-archive-rename|required-lookup|primary` 시나리오이므로 이름/구문 변화 뒤에도 `EXISTENCE/EXISTS`가 기대됨 |

권장 합계는 `PRESERVE 11`, `REPLACE 14`, `UNSUPPORTED 0`이다. `PARTIAL` extraction과 진단은 숨기지 않고 baseline expectation에 함께 고정한다.

## 3. REPLACE 세부 범주

| 기대할 일반 의미 | 건수 | 대표 코드 형태 |
|---|---:|---|
| numeric/range constraint | 4 | `amount <= 0`, `amount < 0`, `days < 1`, `weight <= 0 || weight > 100` |
| string presence/shape constraint | 9 | `isBlank`, `isEmpty`, `length < 3`, `contains("@")`, null+blank composite |
| existence scenario across mutation | 1 | `Optional.isEmpty()` 후 throw, 이후 `get()` |

이 항목들은 `quantity < 0` 같은 repo-specific 문장을 YAML 룰로 옮기자는 뜻이 아니다. 향후 generic predicate/constraint primitive가 표현해야 할 의미를 baseline label로 먼저 명시하는 것이다.

## 4. Delegated hardcoding 판정

이번 5+1 corpus에서는 `DelegatedGuardRule` 계열의 domain-specific candidate가 0건이었다. 따라서 자동으로 `PRESERVE`할 항목도 없다. 이후 실제 corpus에서 발견되면 기본 제안은 `REPLACE`; 일반 의미로 교정할 수 없는 경우에만 명시적 diagnostic을 가진 `UNSUPPORTED`다.

## 5. 승인 게이트

이 제안이 승인되기 전에는 `baseline-manifest.json`을 만들거나 approved metadata를 기록하지 않는다. 승인 후에만 위 그룹을 exact/scenario entry로 전개하고 G01 3회 결정성 검증을 수행한다.

승인 결과는 [baseline-manifest.json](../../../../src/test/resources/rule-based-static-analysis/baseline/v1/baseline-manifest.json)에 기록되었다. G01은 3회 canonical snapshot 결정성은 통과했고, 현재 Java 관찰과 `REPLACE` 기대값의 차이로 의미 결과는 `FAIL`을 반환했다.
